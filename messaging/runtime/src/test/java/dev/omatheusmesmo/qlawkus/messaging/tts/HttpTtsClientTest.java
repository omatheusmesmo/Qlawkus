package dev.omatheusmesmo.qlawkus.messaging.tts;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpTtsClientTest {

    @Test
    void synthesize_returnsAudioBytesOnSuccess() throws Exception {
        byte[] audio = {(byte) 0xFF, (byte) 0xFB, 0x10, 0x20};
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/audio/speech", exchange -> {
            exchange.sendResponseHeaders(200, audio.length);
            try (var out = exchange.getResponseBody()) {
                out.write(audio);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            HttpTtsClient client = new HttpTtsClient();

            byte[] result = client.synthesize(provider("http://localhost:" + port, ""), "olá mundo");

            assertArrayEquals(audio, result);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void synthesize_throwsOnApiError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/audio/speech", exchange -> {
            byte[] body = "{\"error\":\"bad voice\"}".getBytes();
            exchange.sendResponseHeaders(400, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            HttpTtsClient client = new HttpTtsClient();

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> client.synthesize(provider("http://localhost:" + port, ""), "hi"));
            assertTrue(ex.getMessage().contains("400"));
        } finally {
            server.stop(0);
        }
    }

    private TtsConfig.TtsProvider provider(String baseUrl, String apiKey) {
        return new TtsConfig.TtsProvider() {
            @Override public String kind() { return "openai"; }
            @Override public String baseUrl() { return baseUrl; }
            @Override public Optional<String> apiKey() { return Optional.ofNullable(apiKey); }
            @Override public String model() { return "tts-1"; }
            @Override public String voice() { return "pt_br"; }
            @Override public String responseFormat() { return "mp3"; }
        };
    }
}
