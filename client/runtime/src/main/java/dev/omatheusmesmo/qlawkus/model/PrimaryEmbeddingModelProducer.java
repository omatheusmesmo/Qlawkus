package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.Optional;

/**
 * Builds the primary embedding model from the {@code qlawkus.embedding.*} keys that
 * {@link LlmKindConfigSourceFactory} resolves from the selected provider kind.
 *
 * <p>Uses the langchain4j upstream {@code OpenAiEmbeddingModel} rather than the quarkus-langchain4j
 * openai extension because the embedding needs {@code dimensions} (Matryoshka reduction, e.g.
 * text-embedding-3-large at 1024) and {@code customParameters} (NVIDIA {@code input_type}) — neither
 * of which the extension's {@code EmbeddingModelConfig} exposes today.
 */
@ApplicationScoped
public class PrimaryEmbeddingModelProducer {

    @Produces
    @ApplicationScoped
    @PrimaryEmbedding
    public EmbeddingModel primaryEmbeddingModel(
            @ConfigProperty(name = "qlawkus.embedding.base-url") String baseUrl,
            @ConfigProperty(name = "qlawkus.embedding.api-key") String apiKey,
            @ConfigProperty(name = "qlawkus.embedding.model-name") String modelName,
            @ConfigProperty(name = "qlawkus.embedding.dimensions") Optional<Integer> dimensions,
            @ConfigProperty(name = "qlawkus.embedding.input-type") Optional<String> inputType) {

        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName);

        // Only providers that support Matryoshka reduction (OpenAI text-embedding-3-*) accept the
        // dimensions param; for native-dimension models it is absent and must not be sent.
        dimensions.ifPresent(builder::dimensions);
        inputType.ifPresent(type -> builder.customParameters(Map.<String, Object>of("input_type", type)));

        Log.infof("Primary embedding model: %s (base-url=%s, dimensions=%s, input-type=%s)",
                modelName, baseUrl, dimensions.map(String::valueOf).orElse("native"), inputType.orElse("none"));

        return builder.build();
    }
}
