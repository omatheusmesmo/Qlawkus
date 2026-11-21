package dev.omatheusmesmo.qlawkus.messaging.tts;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElevenLabsTtsClientTest {

    private final ElevenLabsTtsClient client = new ElevenLabsTtsClient(
            (baseUrl, voice, outputFormat, apiKey, request) -> {
                throw new AssertionError("no request expected");
            });

    @Test
    void kindIsElevenlabs() {
        assertEquals("elevenlabs", client.kind());
    }

    @Test
    void synthesize_throwsWhenApiKeyMissing() {
        assertThrows(IllegalStateException.class, () -> client.synthesize(provider(null), "hi"));
    }

    private TtsConfig.TtsProvider provider(String apiKey) {
        return new TtsConfig.TtsProvider() {
            @Override public String kind() { return "elevenlabs"; }
            @Override public String baseUrl() { return "http://localhost"; }
            @Override public Optional<String> apiKey() { return Optional.ofNullable(apiKey); }
            @Override public String model() { return "m"; }
            @Override public String voice() { return "v"; }
            @Override public String responseFormat() { return "mp3"; }
            @Override public Optional<String> fallback() { return Optional.empty(); }
        };
    }
}
