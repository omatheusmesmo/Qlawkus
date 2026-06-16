package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
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
public class FallbackChatModel implements ChatModel {

    private final ChatModel delegate;
    private final ChatModel fallback;
    private final CircuitBreaker circuitBreaker;
    private final ModelFallbackConfig config;

    @Inject
    public FallbackChatModel(
            @ModelName("primary") ChatModel delegate,
            @ModelName("fallback") ChatModel fallback,
            CircuitBreaker circuitBreaker,
            ModelFallbackConfig config) {
        this.delegate = delegate;
        this.fallback = fallback;
        this.circuitBreaker = circuitBreaker;
        this.config = config;
        Log.info("FallbackChatModel initialized with @ModelName(\"primary\") delegate");
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        if (!config.fallbackEnabled()) {
            return delegate.doChat(request);
        }

        if (circuitBreaker.isOpen()) {
            Log.info("Circuit breaker OPEN — routing chat to Ollama fallback");
            return fallback.doChat(sanitizeForOllama(request));
        }

        List<Duration> delays = config.retryDelaysAsDurations();
        Exception lastException = null;

        try {
            ChatResponse response = delegate.doChat(request);
            if (circuitBreaker.isHalfOpen()) {
                circuitBreaker.recordSuccess();
            }
            return response;
        } catch (Exception e) {
            lastException = e;
            if (!isRetryable(e)) {
                throw e;
            }
        }

        for (int attempt = 0; attempt < delays.size(); attempt++) {
            Duration delay = delays.get(attempt);
            Log.warnf("ChatModel attempt %d/%d failed: %s — retrying in %ds",
                    attempt + 1, delays.size() + 1, lastException.getMessage(), delay.toSeconds());
            sleep(delay);

            try {
                ChatResponse response = delegate.doChat(request);
                if (circuitBreaker.isHalfOpen()) {
                    circuitBreaker.recordSuccess();
                }
                return response;
            } catch (Exception e) {
                lastException = e;
            }
        }

        Log.warnf("All %d chat attempts failed — opening circuit breaker and falling back to Ollama",
                delays.size() + 1);
        circuitBreaker.recordFailure();

        try {
            return fallback.doChat(sanitizeForOllama(request));
        } catch (Exception fallbackEx) {
            Log.errorf(fallbackEx, "Fallback Ollama chat also failed");
            throw new RuntimeException("Primary and fallback chat models both failed. Primary: "
                    + lastException.getMessage() + ". Fallback: " + fallbackEx.getMessage(), fallbackEx);
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry backoff", e);
        }
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
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

    private ChatRequest sanitizeForOllama(ChatRequest request) {
        OllamaChatRequestParameters ollamaParams = OllamaChatRequestParameters.builder()
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
        return ChatRequest.builder()
                .messages(request.messages())
                .parameters(ollamaParams)
                .build();
    }
}
