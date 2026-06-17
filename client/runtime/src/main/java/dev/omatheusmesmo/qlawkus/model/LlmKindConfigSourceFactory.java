package dev.omatheusmesmo.qlawkus.model;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.PropertiesConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The single place that knows what a {@code kind} is. Resolves {@code qlawkus.openai.kind} (and the
 * optional {@code qlawkus.openai.embedding.kind}) plus any {@code LLM_*} overrides into the concrete
 * config keys that the rest of the system reads as dumb consumers:
 *
 * <ul>
 *   <li>{@code quarkus.langchain4j.openai."primary".{base-url,api-key,chat-model.model-name}} —
 *       consumed by the quarkus-langchain4j openai extension to build the chat model.</li>
 *   <li>{@code qlawkus.embedding.{base-url,api-key,model-name,dimensions,input-type}} —
 *       consumed by {@link PrimaryEmbeddingModelProducer}.</li>
 *   <li>{@code quarkus.langchain4j.pgvector.dimension} — consumed by the pgvector store.</li>
 * </ul>
 *
 * <p>Precedence (high to low): an explicit {@code quarkus.langchain4j.*} value (e.g. via env) wins
 * over this source (low ordinal); the friendly {@code LLM_*} override wins over the kind default;
 * the kind default fills the rest.
 */
public class LlmKindConfigSourceFactory implements ConfigSourceFactory {

    private static final int ORDINAL = 90;
    private static final String NO_KEY_PLACEHOLDER = "no-key";

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        ProviderKind kind = ProviderKind.of(value(context, "openai", "qlawkus.openai.kind", "LLM_KIND"));

        // Overrides use the friendly LLM_* env alias only. We must NOT read the native
        // quarkus.langchain4j.openai."primary".{api-key,base-url} keys here: those are what this
        // factory emits, and reading them during config bootstrap is circular. To override directly,
        // set the native key — it has a higher ordinal than this source, so it still wins downstream.
        // "dummy" is the project-wide "no real key" sentinel (see QlawkusTestUtils#usesLLM), but the
        // quarkus-langchain4j openai extension treats a "dummy" api-key as unset and fails to start.
        // Map it (and a missing key) to a harmless placeholder so the app boots keyless (LLM off).
        String apiKey = value(context, NO_KEY_PLACEHOLDER, "LLM_API_KEY");
        if ("dummy".equals(apiKey)) {
            apiKey = NO_KEY_PLACEHOLDER;
        }
        String baseUrl = value(context, kind.baseUrl(), "LLM_BASE_URL");
        String chatModel = value(context, null, "LLM_CHAT_MODEL");
        if (chatModel == null) {
            chatModel = kind.chatModel().orElseThrow(() -> new IllegalArgumentException(
                    "Provider kind '%s' has no default chat model; set LLM_CHAT_MODEL.".formatted(kind)));
        }

        ProviderKind embKind = ProviderKind.of(
                value(context, kind.name(), "qlawkus.openai.embedding.kind", "LLM_EMBEDDING_KIND"));
        String embApiKey = value(context, apiKey, "LLM_EMBEDDING_API_KEY");
        String embBaseUrlDefault = embKind == kind ? baseUrl : embKind.baseUrl();
        String embBaseUrl = value(context, embBaseUrlDefault, "LLM_EMBEDDING_BASE_URL");

        // Note: we do NOT read the native quarkus embedding-model.model-name here — it carries the
        // extension's @WithDefault ("text-embedding-ada-002"), which would shadow the kind default.
        // Override the embedding model via LLM_EMBEDDING_MODEL.
        String embModel = value(context, null, "LLM_EMBEDDING_MODEL");
        if (embModel == null) {
            embModel = embKind.embeddingModel().orElseThrow(() -> new IllegalArgumentException(
                    ("Embedding kind '%s' provides no embeddings. Set LLM_EMBEDDING_KIND to a provider "
                            + "that does (openai, nvidia, deepseek, mistral) or set LLM_EMBEDDING_MODEL.")
                            .formatted(embKind)));
        }

        String embDimsDefault = embKind.embeddingDimensions() > 0
                ? String.valueOf(embKind.embeddingDimensions())
                : null;
        String embDims = value(context, embDimsDefault, "EMBEDDING_DIMENSION");
        if (embDims == null) {
            throw new IllegalArgumentException(
                    "Embedding dimension is unknown for kind '%s'; set EMBEDDING_DIMENSION.".formatted(embKind));
        }
        Map<String, String> emitted = new HashMap<>();
        emitted.put("quarkus.langchain4j.openai.\"primary\".base-url", baseUrl);
        emitted.put("quarkus.langchain4j.openai.\"primary\".api-key", apiKey);
        emitted.put("quarkus.langchain4j.openai.\"primary\".chat-model.model-name", chatModel);
        emitted.put("qlawkus.embedding.base-url", embBaseUrl);
        emitted.put("qlawkus.embedding.api-key", embApiKey);
        emitted.put("qlawkus.embedding.model-name", embModel);
        // pgvector table dimension is always set; the OpenAI `dimensions` request param is only sent
        // for providers that accept it (Matryoshka) — sending it to NVIDIA/mistral/ollama errors.
        emitted.put("quarkus.langchain4j.pgvector.dimension", embDims);
        if (embKind.sendsDimensionsParam()) {
            emitted.put("qlawkus.embedding.dimensions", embDims);
        }
        embKind.embeddingInputType().ifPresent(type -> emitted.put("qlawkus.embedding.input-type", type));

        return List.of(new PropertiesConfigSource(emitted, "qlawkus-llm-kind", ORDINAL));
    }

    /**
     * Runs below application.properties/env so an explicit {@code quarkus.langchain4j.*} value still wins.
     */
    @Override
    public OptionalInt getPriority() {
        return OptionalInt.of(ORDINAL);
    }

    private static String value(ConfigSourceContext context, String fallback, String... keys) {
        for (String key : keys) {
            ConfigValue configValue = context.getValue(key);
            if (configValue != null && configValue.getValue() != null && !configValue.getValue().isBlank()) {
                return configValue.getValue();
            }
        }
        return fallback;
    }
}
