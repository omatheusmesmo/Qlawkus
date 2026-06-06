package dev.omatheusmesmo.qlawkus.messaging.transcription;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhisperTranscriptionServiceTest {

    @Test
    void transcribe_throwsWhenApiKeyIsBlank() {
        WhisperTranscriptionService service = new WhisperTranscriptionService(
            config("", "whisper-1", "https://api.openai.com"));
        service.setHttpClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.transcribe(new byte[]{1, 2, 3}));
        assertTrue(ex.getMessage().contains("api-key"));
    }

    @Test
    void transcribe_returnsTextOnSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/audio/transcriptions", exchange -> {
            byte[] body = "{\"text\":\"Hello from Whisper\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            WhisperTranscriptionService service = new WhisperTranscriptionService(
                config("sk-test", "whisper-1", "http://localhost:" + port));
            service.setHttpClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());

            String result = service.transcribe(new byte[]{1, 2, 3});

            assertEquals("Hello from Whisper", result);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void transcribe_throwsOnApiError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/audio/transcriptions", exchange -> {
            byte[] body = "{\"error\":\"Invalid API key\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            WhisperTranscriptionService service = new WhisperTranscriptionService(
                config("bad-key", "whisper-1", "http://localhost:" + port));
            service.setHttpClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.transcribe(new byte[]{1, 2, 3}));
            assertTrue(ex.getMessage().contains("401"));
        } finally {
            server.stop(0);
        }
    }

    private VoiceTranscriptionConfig config(String apiKey, String model, String baseUrl) {
        return new VoiceTranscriptionConfig() {
            @Override public String apiKey() { return apiKey; }
            @Override public String model() { return model; }
            @Override public String baseUrl() { return baseUrl; }
        };
    }
}
