package dev.omatheusmesmo.qlawkus.messaging.tts;

import io.quarkus.arc.All;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class TtsRouter {

    @Inject
    TtsConfig config;

    @Inject
    @All
    List<TtsClient> clients;

    public boolean enabled() {
        return config.enabled() && !config.providers().isEmpty();
    }

    /**
     * Synthesizes speech for the response text in the requested language,
     * falling back to the configured default language when no provider matches.
     */
    public byte[] synthesize(String text, String language) {
        TtsConfig.TtsProvider provider = resolveProvider(language);
        try {
            return clientFor(provider.kind()).synthesize(provider, text);
        } catch (RuntimeException primaryFailure) {
            TtsConfig.TtsProvider fallback = provider.fallback()
                    .filter(key -> !key.isBlank())
                    .map(key -> config.providers().get(key))
                    .orElse(null);
            if (fallback == null) {
                throw primaryFailure;
            }
            Log.warnf("TTS provider for language='%s' failed (%s); using fallback '%s'",
                    language, primaryFailure.getMessage(), provider.fallback().orElse(""));
            return clientFor(fallback.kind()).synthesize(fallback, text);
        }
    }

    private TtsClient clientFor(String kind) {
        return clients.stream()
                .filter(client -> client.kind().equalsIgnoreCase(kind))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No TTS client registered for provider kind '" + kind + "'"));
    }

    private TtsConfig.TtsProvider resolveProvider(String language) {
        return providerFor(language)
                .or(() -> providerFor(config.defaultLanguage()))
                .orElseThrow(() -> new IllegalStateException(
                        "No TTS provider configured for language '" + language
                                + "' or default '" + config.defaultLanguage() + "'"));
    }

    private Optional<TtsConfig.TtsProvider> providerFor(String language) {
        if (language == null || language.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(config.providers().get(normalize(language)));
    }

    private String normalize(String language) {
        String trimmed = language.trim().toLowerCase(Locale.ROOT);
        int sep = Math.max(trimmed.indexOf('-'), trimmed.indexOf('_'));
        return sep > 0 ? trimmed.substring(0, sep) : trimmed;
    }
}
