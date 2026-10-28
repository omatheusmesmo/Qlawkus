package dev.omatheusmesmo.qlawkus.messaging.tts;

import dev.omatheusmesmo.qlawkus.messaging.MessagingLog;
import io.quarkus.arc.All;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
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
        long startNanos = System.nanoTime();
        try {
            byte[] audio = clientFor(provider.kind()).synthesize(provider, text);
            logSynthesized(provider.kind(), language, text, startNanos);
            return audio;
        } catch (RuntimeException primaryFailure) {
            TtsConfig.TtsProvider fallback = provider.fallback()
                    .filter(key -> !key.isBlank())
                    .map(key -> config.providers().get(key))
                    .orElse(null);
            if (fallback == null) {
                logFailed(provider.kind(), language, text, startNanos, primaryFailure);
                throw primaryFailure;
            }
            Log.warnf("TTS provider for language='%s' failed (%s); using fallback '%s'",
                    language, MessagingLog.exceptionName(primaryFailure), provider.fallback().orElse(""));
            return synthesizeWithFallback(fallback, language, text);
        }
    }

    private byte[] synthesizeWithFallback(TtsConfig.TtsProvider fallback, String language, String text) {
        long startNanos = System.nanoTime();
        try {
            byte[] audio = clientFor(fallback.kind()).synthesize(fallback, text);
            logSynthesized(fallback.kind(), language, text, startNanos);
            return audio;
        } catch (RuntimeException fallbackFailure) {
            logFailed(fallback.kind(), language, text, startNanos, fallbackFailure);
            throw fallbackFailure;
        }
    }

    private static void logSynthesized(String provider, String language, String text, long startNanos) {
        MessagingLog.info(null, null, "Speech synthesized provider=%s language=%s chars=%d elapsedMs=%d outcome=ok",
                provider, language, text.length(), elapsedMs(startNanos));
    }

    private static void logFailed(String provider, String language, String text, long startNanos,
                                  RuntimeException failure) {
        MessagingLog.warn(null, null,
                "Speech synthesis failed provider=%s language=%s chars=%d elapsedMs=%d outcome=error exception=%s",
                provider, language, text.length(), elapsedMs(startNanos), MessagingLog.exceptionName(failure));
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
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
