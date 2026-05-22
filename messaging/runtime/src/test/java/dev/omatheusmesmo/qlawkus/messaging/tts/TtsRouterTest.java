package dev.omatheusmesmo.qlawkus.messaging.tts;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtsRouterTest {

    @Test
    void selectsProviderMatchingLanguage() {
        RecordingClient client = new RecordingClient();
        TtsRouter router = router(client, true, "en",
                Map.of("pt", provider("pt-model"), "en", provider("en-model")));

        router.synthesize("olá", "pt");

        assertEquals("pt-model", client.usedProvider.model());
    }

    @Test
    void normalizesRegionalLanguageTag() {
        RecordingClient client = new RecordingClient();
        TtsRouter router = router(client, true, "en",
                Map.of("pt", provider("pt-model")));

        router.synthesize("olá", "pt-BR");

        assertEquals("pt-model", client.usedProvider.model());
    }

    @Test
    void fallsBackToDefaultLanguageWhenUnmatched() {
        RecordingClient client = new RecordingClient();
        TtsRouter router = router(client, true, "en",
                Map.of("en", provider("en-model")));

        router.synthesize("hi", "de");

        assertEquals("en-model", client.usedProvider.model());
    }

    @Test
    void throwsWhenNoProviderResolves() {
        TtsRouter router = router(new RecordingClient(), true, "en", Map.of("pt", provider("pt-model")));

        assertThrows(IllegalStateException.class, () -> router.synthesize("hi", "de"));
    }

    @Test
    void enabledReflectsConfigAndProviders() {
        assertTrue(router(new RecordingClient(), true, "en", Map.of("pt", provider("m"))).enabled());
        assertFalse(router(new RecordingClient(), false, "en", Map.of("pt", provider("m"))).enabled());
        assertFalse(router(new RecordingClient(), true, "en", Map.of()).enabled());
    }

    private TtsRouter router(TtsClient client, boolean enabled, String defaultLang,
                             Map<String, TtsConfig.TtsProvider> providers) {
        TtsRouter router = new TtsRouter();
        router.clients = List.of(client);
        router.config = config(enabled, defaultLang, providers);
        return router;
    }

    private TtsConfig config(boolean enabled, String defaultLang,
                             Map<String, TtsConfig.TtsProvider> providers) {
        return new TtsConfig() {
            @Override public boolean enabled() { return enabled; }
            @Override public String defaultLanguage() { return defaultLang; }
            @Override public Map<String, TtsProvider> providers() { return providers; }
        };
    }

    private TtsConfig.TtsProvider provider(String model) {
        return providerWith("openai", model, null);
    }

    private TtsConfig.TtsProvider providerWith(String kind, String model, String fallback) {
        return new TtsConfig.TtsProvider() {
            @Override public String kind() { return kind; }
            @Override public String baseUrl() { return "http://tts"; }
            @Override public Optional<String> apiKey() { return Optional.empty(); }
            @Override public String model() { return model; }
            @Override public String voice() { return "v"; }
            @Override public String responseFormat() { return "mp3"; }
            @Override public Optional<String> fallback() { return Optional.ofNullable(fallback); }
        };
    }

    @Test
    void usesFallbackProviderWhenPrimaryFails() {
        RecordingClient ok = new RecordingClient();
        TtsRouter router = new TtsRouter();
        router.clients = List.of(new FailingClient(), ok);
        router.config = config(true, "en", Map.of(
                "en", providerWith("failing", "primary-model", "en_local"),
                "en_local", providerWith("openai", "fallback-model", null)));

        router.synthesize("hi", "en");

        assertEquals("fallback-model", ok.usedProvider.model());
    }

    @Test
    void rethrowsWhenPrimaryFailsAndNoFallback() {
        TtsRouter router = new TtsRouter();
        router.clients = List.of(new FailingClient());
        router.config = config(true, "en", Map.of("en", providerWith("failing", "m", null)));

        assertThrows(RuntimeException.class, () -> router.synthesize("hi", "en"));
    }

    private static class FailingClient implements TtsClient {
        @Override
        public String kind() {
            return "failing";
        }

        @Override
        public byte[] synthesize(TtsConfig.TtsProvider provider, String text) {
            throw new RuntimeException("primary down");
        }
    }

    private static class RecordingClient implements TtsClient {
        TtsConfig.TtsProvider usedProvider;

        @Override
        public String kind() {
            return "openai";
        }

        @Override
        public byte[] synthesize(TtsConfig.TtsProvider provider, String text) {
            this.usedProvider = provider;
            return new byte[]{1};
        }
    }
}
