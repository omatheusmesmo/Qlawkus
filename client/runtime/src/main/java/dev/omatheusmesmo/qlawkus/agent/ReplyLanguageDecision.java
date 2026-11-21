package dev.omatheusmesmo.qlawkus.agent;

import java.util.List;

/** Shared stateless language selection; distributions can replace the registered-model implementation. */
public interface ReplyLanguageDecision {
    /** Empty choices permits any language code; null leaves inference to the main agent's prompt. */
    String decide(String message, String recognizerHint, String stableLanguage, List<String> choices, String fallback);
}
