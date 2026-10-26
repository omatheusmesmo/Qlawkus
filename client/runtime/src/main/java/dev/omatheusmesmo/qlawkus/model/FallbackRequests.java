package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;

/**
 * Rebuilds a request that was merged with the primary model's defaults into one the fallback model
 * can take. The merged request carries the primary's model id and whatever provider-specific
 * parameters it set, none of which belong on a different model: leaving the model id in place would
 * ask the fallback endpoint for the primary's model, so the fallback applies its own configured one.
 * Only the parameters every OpenAI-compatible endpoint, hosted or Ollama, understands are kept.
 */
final class FallbackRequests {

    private FallbackRequests() {
    }

    static ChatRequest forFallback(ChatRequest request) {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .temperature(request.temperature())
                .topP(request.topP())
                .maxOutputTokens(request.maxOutputTokens())
                .stopSequences(request.stopSequences())
                .toolSpecifications(request.toolSpecifications())
                .toolChoice(request.toolChoice())
                .responseFormat(request.responseFormat())
                .build();
        return ChatRequest.builder()
                .messages(request.messages())
                .parameters(parameters)
                .build();
    }
}
