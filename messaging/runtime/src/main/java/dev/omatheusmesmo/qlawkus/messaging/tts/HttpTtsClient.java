package dev.omatheusmesmo.qlawkus.messaging.tts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@ApplicationScoped
public class HttpTtsClient implements TtsClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String kind() {
        return "openai";
    }

    @Override
    public byte[] synthesize(TtsConfig.TtsProvider provider, String text) {
        byte[] body = buildBody(provider, text);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(provider.baseUrl() + "/v1/audio/speech"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofSeconds(120));

        provider.apiKey()
                .filter(key -> !key.isBlank())
                .ifPresent(key -> builder.header("Authorization", "Bearer " + key));

        try {
            HttpResponse<byte[]> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "TTS API error %d: %s".formatted(response.statusCode(),
                                new String(response.body())));
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("TTS request interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to call TTS API", e);
        }
    }

    private byte[] buildBody(TtsConfig.TtsProvider provider, String text) {
        try {
            return MAPPER.writeValueAsBytes(Map.of(
                    "model", provider.model(),
                    "input", text,
                    "voice", provider.voice(),
                    "response_format", provider.responseFormat()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build TTS request body", e);
        }
    }
}
