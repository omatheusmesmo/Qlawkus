package dev.omatheusmesmo.qlawkus.messaging.tts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@ApplicationScoped
public class ElevenLabsTtsClient implements TtsClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;

    public ElevenLabsTtsClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build());
    }

    ElevenLabsTtsClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String kind() {
        return "elevenlabs";
    }

    @Override
    public byte[] synthesize(TtsConfig.TtsProvider provider, String text) {
        String apiKey = provider.apiKey()
                .filter(key -> !key.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "ElevenLabs provider requires an api-key"));

        byte[] body = buildBody(provider, text);
        String uri = provider.baseUrl() + "/v1/text-to-speech/" + provider.voice()
                + "?output_format=" + outputFormat(provider.responseFormat());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofSeconds(120))
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "ElevenLabs API error %d: %s".formatted(response.statusCode(),
                                new String(response.body())));
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("ElevenLabs request interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to call ElevenLabs API", e);
        }
    }

    private byte[] buildBody(TtsConfig.TtsProvider provider, String text) {
        try {
            return MAPPER.writeValueAsBytes(Map.of(
                    "text", text,
                    "model_id", provider.model()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build ElevenLabs request body", e);
        }
    }

    private String outputFormat(String responseFormat) {
        return "mp3".equalsIgnoreCase(responseFormat) ? "mp3_44100_128" : responseFormat;
    }
}
