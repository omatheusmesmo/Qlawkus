package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.Capability;
import io.quarkus.logging.Log;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Alternative
@Priority(1)
@ApplicationScoped
public class FallbackStreamingChatModel implements StreamingChatModel {

    private final StreamingChatModel delegate;
    private final StreamingChatModel fallback;
    private final CircuitBreaker circuitBreaker;
    private final ModelFallbackConfig config;

    @Inject
    public FallbackStreamingChatModel(
            @ModelName("nvidia") StreamingChatModel delegate,
            @ModelName("ollama-fallback") StreamingChatModel fallback,
            CircuitBreaker circuitBreaker,
            ModelFallbackConfig config) {
        this.delegate = delegate;
        this.fallback = fallback;
        this.circuitBreaker = circuitBreaker;
        this.config = config;
        Log.info("FallbackStreamingChatModel initialized with @ModelName(\"nvidia\") delegate");
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        if (!config.fallbackEnabled()) {
            delegate.doChat(request, handler);
            return;
        }

        if (circuitBreaker.isOpen()) {
            Log.info("Circuit breaker OPEN — routing streaming chat to Ollama fallback");
            fallback.doChat(sanitizeForOllama(request), handler);
            return;
        }

        List<Duration> delays = config.retryDelaysAsDurations();
        StreamingChatResponseHandler retryHandler = new RetryStreamingHandler(
                request, handler, delegate, fallback, circuitBreaker, delays, 0);
        delegate.doChat(request, retryHandler);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    private static class RetryStreamingHandler implements StreamingChatResponseHandler {

        private final ChatRequest request;
        private final StreamingChatResponseHandler downstream;
        private final StreamingChatModel delegate;
        private final StreamingChatModel fallback;
        private final CircuitBreaker circuitBreaker;
        private final List<Duration> delays;
        private final int attempt;
        private volatile boolean partialSent;

        RetryStreamingHandler(
                ChatRequest request,
                StreamingChatResponseHandler downstream,
                StreamingChatModel delegate,
                StreamingChatModel fallback,
                CircuitBreaker circuitBreaker,
                List<Duration> delays,
                int attempt) {
            this.request = request;
            this.downstream = downstream;
            this.delegate = delegate;
            this.fallback = fallback;
            this.circuitBreaker = circuitBreaker;
            this.delays = delays;
            this.attempt = attempt;
        }

        @Override
        public void onPartialResponse(String partial) {
            partialSent = true;
            downstream.onPartialResponse(partial);
        }

        @Override
        public void onPartialResponse(PartialResponse partial, PartialResponseContext context) {
            partialSent = true;
            downstream.onPartialResponse(partial, context);
        }

        @Override
        public void onPartialThinking(PartialThinking thinking) {
            downstream.onPartialThinking(thinking);
        }

        @Override
        public void onPartialThinking(PartialThinking thinking, PartialThinkingContext context) {
            downstream.onPartialThinking(thinking, context);
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall) {
            downstream.onPartialToolCall(partialToolCall);
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
            downstream.onPartialToolCall(partialToolCall, context);
        }

        @Override
        public void onCompleteToolCall(CompleteToolCall completeToolCall) {
            downstream.onCompleteToolCall(completeToolCall);
        }

        @Override
        public void onCompleteResponse(ChatResponse response) {
            if (circuitBreaker.isHalfOpen()) {
                circuitBreaker.recordSuccess();
            }
            downstream.onCompleteResponse(response);
        }

        @Override
        public void onError(Throwable error) {
            if (partialSent) {
                Log.warnf("Streaming error after partial response committed — cannot retry, forwarding error");
                downstream.onError(error);
                return;
            }

            if (!isRetryable(error) || attempt >= delays.size()) {
            if (isRetryable(error)) {
                    Log.warnf("All %d streaming attempts failed — opening circuit breaker and falling back to Ollama", delays.size() + 1);
                    circuitBreaker.recordFailure();
                    fallback.doChat(sanitizeForOllama(request), downstream);
                } else {
                    downstream.onError(error);
                }
                return;
            }

            Duration delay = delays.get(attempt);
            Log.warnf("StreamingChatModel attempt %d/%d failed: %s — retrying in %ds",
                    attempt + 1, delays.size() + 1, error.getMessage(), delay.toSeconds());

            try {
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                downstream.onError(ie);
                return;
            }

            StreamingChatResponseHandler nextHandler = new RetryStreamingHandler(
                    request, downstream, delegate, fallback, circuitBreaker, delays, attempt + 1);
            delegate.doChat(request, nextHandler);
        }

        private boolean isRetryable(Throwable error) {
            Throwable current = error;
            while (current != null) {
                String className = current.getClass().getName();
                if (className.contains("NonRetriableException")
                        || className.contains("AuthenticationException")
                        || className.contains("ModelNotFoundException")
                        || className.contains("InvalidRequestException")
                        || className.contains("ContentFilteredException")
                        || className.contains("UnsupportedFeatureException")) {
                    return false;
                }
                current = current.getCause();
            }
            return true;
        }
    }

    private static ChatRequest sanitizeForOllama(ChatRequest request) {
        if (request.frequencyPenalty() == null && request.presencePenalty() == null) {
            return request;
        }
        Log.debug("Stripping frequencyPenalty/presencePenalty for Ollama compatibility");
        return ChatRequest.builder()
                .messages(request.messages())
                .modelName(request.modelName())
                .temperature(request.temperature())
                .topP(request.topP())
                .topK(request.topK())
                .maxOutputTokens(request.maxOutputTokens())
                .stopSequences(request.stopSequences())
                .toolSpecifications(request.toolSpecifications())
                .toolChoice(request.toolChoice())
                .responseFormat(request.responseFormat())
                .build();
    }
}
