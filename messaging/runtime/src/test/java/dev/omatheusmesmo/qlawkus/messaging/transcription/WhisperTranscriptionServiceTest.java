package dev.omatheusmesmo.qlawkus.messaging.transcription;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void transcribeWithLanguageAsksForVerboseJsonAndReturnsTheDetectedLanguageCode() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = capturingServer(body, "{\"text\":\"Olá, tudo bem?\",\"language\":\"portuguese\"}");
        try {
            WhisperTranscriptionService service = serviceFor(server);

            VoiceTranscriptionService.Transcription result =
                    service.transcribe(new byte[]{1, 2, 3}, "audio/wav", null);

            assertEquals("Olá, tudo bem?", result.text());
            assertEquals("pt", result.language());
            assertTrue(body.get().contains("name=\"response_format\"\r\n\r\nverbose_json"), body.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void transcribeWithLanguageSendsTheHintAsAnIsoCode() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = capturingServer(body, "{\"text\":\"oi\",\"language\":\"portuguese\"}");
        try {
            serviceFor(server).transcribe(new byte[]{1, 2, 3}, "audio/wav", "pt-BR");

            assertTrue(body.get().contains("name=\"language\"\r\n\r\npt\r\n"), body.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void transcribeWithLanguageOmitsTheLanguageFieldWithoutAHint() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = capturingServer(body, "{\"text\":\"hi\",\"language\":\"english\"}");
        try {
            VoiceTranscriptionService.Transcription result =
                    serviceFor(server).transcribe(new byte[]{1, 2, 3}, "audio/wav", null);

            assertFalse(body.get().contains("name=\"language\""), body.get());
            assertEquals("en", result.language());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void transcribeWithLanguageRejectsAHintThatIsNotAnIsoCode() {
        WhisperTranscriptionService service = new WhisperTranscriptionService(
            config("sk-test", "whisper-1", "http://localhost:1"));

        assertThrows(IllegalArgumentException.class,
                () -> service.transcribe(new byte[]{1}, "audio/wav", "portuguese language"));
    }

    @Test
    void anUnknownDetectedLanguageNameYieldsNoLanguageCode() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = capturingServer(body, "{\"text\":\"...\",\"language\":\"klingon\"}");
        try {
            VoiceTranscriptionService.Transcription result =
                    serviceFor(server).transcribe(new byte[]{1, 2, 3}, "audio/wav", null);

            assertNull(result.language());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void voiceNotesKeepThePlainRequestAndReturnOnlyTheText() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = capturingServer(body, "{\"text\":\"nota de voz\",\"language\":\"portuguese\"}");
        try {
            String text = serviceFor(server).transcribe(new byte[]{1, 2, 3}, "audio/ogg");

            assertEquals("nota de voz", text);
            assertFalse(body.get().contains("verbose_json"), body.get());
            assertFalse(body.get().contains("name=\"language\""), body.get());
        } finally {
            server.stop(0);
        }
    }

    private HttpServer capturingServer(AtomicReference<String> body, String reply) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/audio/transcriptions", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            try (var stream = exchange.getResponseBody()) {
                stream.write(out);
            }
        });
        server.start();
        return server;
    }

    private WhisperTranscriptionService serviceFor(HttpServer server) {
        WhisperTranscriptionService service = new WhisperTranscriptionService(
            config("sk-test", "whisper-1", "http://localhost:" + server.getAddress().getPort()));
        service.setHttpClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
        return service;
    }

    private VoiceTranscriptionConfig config(String apiKey, String model, String baseUrl) {
        return new VoiceTranscriptionConfig() {
            @Override public Optional<String> apiKey() { return Optional.ofNullable(apiKey); }
            @Override public String model() { return model; }
            @Override public String baseUrl() { return baseUrl; }
        };
    }
}
