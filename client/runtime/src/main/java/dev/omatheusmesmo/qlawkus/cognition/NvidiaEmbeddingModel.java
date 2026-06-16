package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class NvidiaEmbeddingModel implements EmbeddingModel {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final String ENABLED_CONFIG = "qlawkus.nvidia.embedding-model.enabled";
    private static final String MODEL_NAME_CONFIG = "qlawkus.nvidia.embedding-model.model-name";
    private static final String INPUT_TYPE_CONFIG = "qlawkus.nvidia.embedding-model.input-type";
    private static final String DIMENSION_CONFIG = "qlawkus.nvidia.embedding-model.dimension";

    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final String inputType;
    private final boolean nvidiaMode;
    private final int dimension;
    private final HttpClient httpClient;

    @Inject
    public NvidiaEmbeddingModel(
    @ConfigProperty(name = "quarkus.langchain4j.openai.\"primary\".base-url") String baseUrl,
    @ConfigProperty(name = "quarkus.langchain4j.openai.\"primary\".api-key") String apiKey,
            Config config) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.apiKey = apiKey;
        this.nvidiaMode = config.getOptionalValue(ENABLED_CONFIG, Boolean.class).orElse(false);
        this.modelName = config.getOptionalValue(MODEL_NAME_CONFIG, String.class)
                .orElse("nvidia/nv-embedqa-e5-v5");
        this.inputType = config.getOptionalValue(INPUT_TYPE_CONFIG, String.class)
                .orElse("passage");
        this.dimension = config.getOptionalValue(DIMENSION_CONFIG, Integer.class)
                .orElse(1024);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .sslContext(defaultSslContext())
                .build();
        Log.infof("NvidiaEmbeddingModel initialized: nvidiaMode=%s, model=%s, inputType=%s, dimension=%d",
                nvidiaMode, modelName, inputType, dimension);
    }

    @Override
    public Response<Embedding> embed(String text) {
        Response<List<Embedding>> response = embedAll(List.of(TextSegment.from(text)));
        return Response.from(response.content().getFirst(), response.tokenUsage());
    }

    @Override
    public Response<Embedding> embed(TextSegment segment) {
        return embed(segment.text());
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<String> texts = textSegments.stream().map(TextSegment::text).toList();

        EmbeddingRequestBody body = nvidiaMode
                ? new EmbeddingRequestBody(modelName, texts, inputType)
                : new EmbeddingRequestBody(modelName, texts, null);

        try {
            String json = MAPPER.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "embeddings"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200) {
                throw new RuntimeException("Embedding API error %d: %s"
                        .formatted(httpResponse.statusCode(), httpResponse.body()));
            }

            EmbeddingResponseBody response = MAPPER.readValue(httpResponse.body(), EmbeddingResponseBody.class);

            List<Embedding> embeddings = response.data().stream()
                    .map(d -> Embedding.from(d.embedding()))
                    .toList();

            TokenUsage tokenUsage = null;
            if (response.usage() != null) {
                tokenUsage = new TokenUsage(response.usage().promptTokens(), response.usage().totalTokens());
            }

            return Response.from(embeddings, tokenUsage);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Embedding request interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call embedding API", e);
        }
    }

    @Override
    public int dimension() {
        return dimension;
    }

    private static SSLContext defaultSslContext() {
        try {
            return SSLContext.getDefault();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get default SSL context", e);
        }
    }

    record EmbeddingRequestBody(
            @JsonProperty("model") String model,
            @JsonProperty("input") List<String> input,
            @JsonProperty("input_type") String inputType) {}

    record EmbeddingResponseBody(
            @JsonProperty("data") List<EmbeddingData> data,
            @JsonProperty("usage") EmbeddingUsage usage) {}

    record EmbeddingData(
            @JsonProperty("embedding") float[] embedding,
            @JsonProperty("index") int index) {}

    record EmbeddingUsage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("total_tokens") Integer totalTokens) {}
}
