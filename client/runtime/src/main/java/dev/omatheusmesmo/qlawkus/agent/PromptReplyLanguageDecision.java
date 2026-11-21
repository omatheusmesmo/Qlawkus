package dev.omatheusmesmo.qlawkus.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import dev.omatheusmesmo.qlawkus.config.ReplyLanguageConfig;
import dev.omatheusmesmo.qlawkus.model.ReplyLanguageModel;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/** Optional classification using the existing model registration, shared by every channel. */
@DefaultBean
@ApplicationScoped
public class PromptReplyLanguageDecision implements ReplyLanguageDecision {
    @Inject ReplyLanguageModel model;
    @Inject ReplyLanguageConfig config;
    @Inject ObjectMapper json;

    @Override
    public String decide(String message, String recognizerHint, String stableLanguage, List<String> choices, String fallback) {
        if (!config.enabled() || message == null || message.isBlank()) return fallback;
        try {
            var input = json.createObjectNode();
            int end = Math.min(message.length(), 2000);
            if (end > 0 && Character.isHighSurrogate(message.charAt(end - 1))) end--;
            input.put("utterance", message.substring(0, end));
            input.put("recognizedLanguage", recognizerHint);
            input.put("stableLanguage", stableLanguage);
            input.set("choices", json.valueToTree(choices));
            String answer = model.classify(json.writeValueAsString(input));
            if (answer == null || answer.length() > 256) return fallback;
            answer = answer.trim();
            if (answer.matches("[a-zA-Z]{2,3}(?:[-_][a-zA-Z0-9]{2,8})*")) return answer;
            // Some registered models return a JSON object despite the code-only instruction.
            // Accept only the bounded, exact schema, never prose or an embedded code fragment.
            JsonNode result = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(answer);
            return result != null && result.isObject() && result.size() == 1
                    && result.path("language").isTextual() ? result.path("language").textValue() : fallback;
        } catch (Exception failure) {
            // No provider messages: they can contain owner text or authentication material.
            return fallback;
        }
    }
}
