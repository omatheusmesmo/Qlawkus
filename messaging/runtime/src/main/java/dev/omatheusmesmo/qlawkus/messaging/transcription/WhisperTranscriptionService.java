package dev.omatheusmesmo.qlawkus.messaging.transcription;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@ApplicationScoped
public class WhisperTranscriptionService implements VoiceTranscriptionService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final VoiceTranscriptionConfig config;
    private HttpClient httpClient;

    @Inject
    public WhisperTranscriptionService(VoiceTranscriptionConfig config) {
        this.config = config;
    }

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
    public String transcribe(byte[] audioBytes) {
        if (config.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "Whisper transcription requires qlawkus.messaging.transcription.api-key to be configured");
        }

        String boundary = "----WhisperBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipartBody(boundary, audioBytes, config.model());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/v1/audio/transcriptions"))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofSeconds(120))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Whisper API error %d: %s".formatted(response.statusCode(), response.body()));
            }
            return MAPPER.readValue(response.body(), TranscriptionResponse.class).text();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Transcription request interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to call Whisper API", e);
        }
    }

    private byte[] buildMultipartBody(String boundary, byte[] audioBytes, String model) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"audio.ogg\"\r\n"
                    + "Content-Type: audio/ogg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(audioBytes);
            out.write(("\r\n--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"model\"\r\n\r\n"
                    + model + "\r\n"
                    + "--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to build multipart body", e);
        }
        return out.toByteArray();
    }

    record TranscriptionResponse(@JsonProperty("text") String text) {}
}
