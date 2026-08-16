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

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The streaming counterpart of {@link FallbackChatModel}, and the one that carries the traffic: the
 * agent's {@code chat} method returns a {@code Multi}, so SSE and every messaging adapter arrive
 * here. Shares {@link PrimaryChatGuard} with {@link FallbackChatModel} - same completions endpoint,
 * so a primary outage detected on either transport opens the one breaker for both.
 *
 * <p>{@code StreamingChatModel.doChat} is callback-based, not blocking, so each attempt is bridged
 * through a {@link CompletableFuture} that {@link #runBridged} resolves from the handler callbacks -
 * that is what lets a callback API sit underneath a guard built around a blocking {@code Supplier}.
 * Partial tokens are forwarded to the real downstream handler as they arrive, live, not buffered
 * until the guard decides the call succeeded; once any partial has been sent, a later error can no
 * longer be retried or silently swapped for the fallback's answer without duplicating or reordering
 * tokens already visible to the user, so {@link #runBridged} resolves the future normally instead of
 * exceptionally in that case - the guard sees a completed attempt, not a failure, and stops.
 *
 * <p>Delegates are called through {@code chat()} rather than {@code doChat()} for the same reason
 * given on {@link FallbackChatModel}: {@code chat()} is what wraps the call in the model's
 * {@code listeners()}, and going straight to {@code doChat()} silently drops every meter, cost
 * estimate and span quarkus-langchain4j attaches there.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class FallbackStreamingChatModel implements StreamingChatModel {

    private final StreamingChatModel delegate;
    private final StreamingChatModel fallback;
    private final PrimaryChatGuard guard;
    private final ModelFallbackConfig config;

    @Inject
    public FallbackStreamingChatModel(
            @ModelName("primary") StreamingChatModel delegate,
            @ModelName("fallback") StreamingChatModel fallback,
            PrimaryChatGuard guard,
            ModelFallbackConfig config) {
        this.delegate = delegate;
        this.fallback = fallback;
        this.guard = guard;
        this.config = config;
        Log.info("FallbackStreamingChatModel initialized with @ModelName(\"primary\") delegate");
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        if (!config.fallbackEnabled()) {
            delegate.chat(request, handler);
            return;
        }

        AtomicBoolean partialSent = new AtomicBoolean(false);
        try {
            guard.call(
                    () -> runBridged(delegate, request, handler, partialSent),
                    () -> runBridged(fallback, sanitizeForOllama(request), handler, partialSent));
        } catch (RuntimeException e) {
            if (!partialSent.get()) {
                handler.onError(e);
            }
            // else: runBridged already forwarded onError to handler before resolving the future
            // normally, precisely so the guard would not see a failure and retry into it.
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

    /**
     * Bridges one callback-based {@code model.chat(request, ...)} attempt into a blocking call the
     * guard can retry: every event is forwarded to {@code downstream} live, and the returned future
     * settles once the model calls {@code onCompleteResponse} or {@code onError}.
     */
    private static ChatResponse runBridged(StreamingChatModel model, ChatRequest request,
            StreamingChatResponseHandler downstream, AtomicBoolean partialSent) {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        StreamingChatResponseHandler bridge = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partial) {
                partialSent.set(true);
                downstream.onPartialResponse(partial);
            }

            @Override
            public void onPartialResponse(PartialResponse partial, PartialResponseContext context) {
                partialSent.set(true);
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
                downstream.onCompleteResponse(response);
                future.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                if (partialSent.get()) {
                    Log.warnf("Streaming error after partial response committed — cannot retry, forwarding error");
                    downstream.onError(error);
                    future.complete(null);
                } else {
                    future.completeExceptionally(error);
                }
            }
        };

        try {
            model.chat(request, bridge);
        } catch (RuntimeException e) {
            future.completeExceptionally(e);
        }

        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
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
