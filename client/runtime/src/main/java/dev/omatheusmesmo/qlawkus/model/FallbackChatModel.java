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

import java.util.Set;

/**
 * Wraps the primary model with retries, a circuit breaker and a switch to the Ollama fallback, all
 * from {@link PrimaryChatGuard}.
 *
 * <p>Both delegates are called through {@code chat()} rather than {@code doChat()}. That distinction
 * decides whether the platform can see the agent at all: {@code chat()} is the entry point whose
 * default implementation wraps the call in the model's {@code listeners()}, and {@code doChat()} is
 * the raw call underneath it. Reaching for {@code doChat()} skips every {@link
 * dev.langchain4j.model.chat.listener.ChatModelListener} quarkus-langchain4j attaches - the token
 * and duration meters, the estimated cost, the tracing spans - and does so silently, since the call
 * itself works perfectly.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class FallbackChatModel implements ChatModel {

    private final ChatModel delegate;
    private final ChatModel fallback;
    private final PrimaryChatGuard guard;
    private final ModelFallbackConfig config;

    @Inject
    public FallbackChatModel(
            @ModelName("primary") ChatModel delegate,
            @ModelName("fallback") ChatModel fallback,
            PrimaryChatGuard guard,
            ModelFallbackConfig config) {
        this.delegate = delegate;
        this.fallback = fallback;
        this.guard = guard;
        this.config = config;
        Log.info("FallbackChatModel initialized with @ModelName(\"primary\") delegate");
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        if (!config.fallbackEnabled()) {
            return delegate.chat(request);
        }
        return guard.call(() -> delegate.chat(request), () -> fallback.chat(sanitizeForOllama(request)));
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    static ChatRequest sanitizeForOllama(ChatRequest request) {
        // Deliberately omit modelName: the request carries the primary provider's model id (e.g.
        // "nvidia/nemotron-3-ultra-550b-a55b"), which the Ollama fallback does not have. Leaving it
        // unset lets the langchain4j-ollama model apply its configured chat-model.model-name instead.
        OllamaChatRequestParameters ollamaParams = OllamaChatRequestParameters.builder()
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
