package dev.omatheusmesmo.qlawkus.messaging.tts;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElevenLabsTtsClientTest {

    @Test
    void kindIsElevenlabs() {
        assertEquals("elevenlabs", new ElevenLabsTtsClient().kind());
    }

    @Test
    void synthesize_postsToVoiceEndpointWithApiKeyHeader() throws Exception {
        byte[] audio = {(byte) 0xFF, (byte) 0xFB, 0x42};
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedKey = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/text-to-speech/", exchange -> {
            capturedPath.set(exchange.getRequestURI().toString());
            capturedKey.set(exchange.getRequestHeaders().getFirst("xi-api-key"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, audio.length);
            try (var out = exchange.getResponseBody()) {
                out.write(audio);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            byte[] result = new ElevenLabsTtsClient()
                    .synthesize(provider("http://localhost:" + port, "secret-key", "voice123", "eleven_multilingual_v2"),
                            "olá mundo");

            assertArrayEquals(audio, result);
            assertTrue(capturedPath.get().startsWith("/v1/text-to-speech/voice123"));
            assertEquals("secret-key", capturedKey.get());
            assertTrue(capturedBody.get().contains("eleven_multilingual_v2"));
            assertTrue(capturedBody.get().contains("olá mundo") || capturedBody.get().contains("\\u00"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void synthesize_throwsWhenApiKeyMissing() {
        assertThrows(IllegalStateException.class,
                () -> new ElevenLabsTtsClient().synthesize(
                        provider("http://localhost", null, "v", "m"), "hi"));
    }

    private TtsConfig.TtsProvider provider(String baseUrl, String apiKey, String voice, String model) {
        return new TtsConfig.TtsProvider() {
            @Override public String kind() { return "elevenlabs"; }
            @Override public String baseUrl() { return baseUrl; }
            @Override public Optional<String> apiKey() { return Optional.ofNullable(apiKey); }
            @Override public String model() { return model; }
            @Override public String voice() { return voice; }
            @Override public String responseFormat() { return "mp3"; }
        };
    }
}
