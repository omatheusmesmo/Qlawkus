package dev.omatheusmesmo.qlawkus.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
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
            return model.classify(json.writeValueAsString(input));
        } catch (Exception failure) {
            // No provider messages: they can contain owner text or authentication material.
            return fallback;
        }
    }
}
