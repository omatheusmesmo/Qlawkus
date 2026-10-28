package dev.omatheusmesmo.qlawkus.model;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Locks the provider-kind base-url resolution precedence.
 *
 * <p>The factory emits config at ordinal 90 and must only honor a value that an out-ranking source
 * (ordinal &gt; 90: env, application.properties) sets. The regression this guards: the native key
 * {@code quarkus.langchain4j.openai."primary".base-url} resolves to the openai extension's
 * {@code @WithDefault} ({@code https://api.openai.com/v1/}) at the lowest ordinal during bootstrap;
 * reading it back silently routed a non-OpenAI provider (e.g. NVIDIA) to the OpenAI endpoint, where
 * the provider key was rejected. An explicit native value (high ordinal) must still be honored, since
 * the embedding inherits the primary base-url from it.
 */
class LlmKindConfigSourceFactoryTest {

    private static final String PRIMARY_BASE_URL = "quarkus.langchain4j.openai.\"primary\".base-url";
    private static final String EMBEDDING_BASE_URL = "qlawkus.embedding.base-url";
    private static final String NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1";
    private static final String OPENAI_DEFAULT_BASE_URL = "https://api.openai.com/v1/";

    private static final int DEFAULT_SOURCE_ORDINAL = 0;
    private static final int OVERRIDE_SOURCE_ORDINAL = 300;

    @Test
    void nvidiaKindIgnoresOpenAiDefaultAndRoutesBothModelsToNvidia() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new LlmKindConfigSourceFactory())
                .withSources(new PropertiesConfigSource(
                        Map.of("LLM_KIND", "nvidia"), "env", OVERRIDE_SOURCE_ORDINAL))
                // Mimics the openai extension's @WithDefault base-url present at the lowest ordinal.
                .withSources(new PropertiesConfigSource(
                        Map.of(PRIMARY_BASE_URL, OPENAI_DEFAULT_BASE_URL),
                        "fake-openai-default", DEFAULT_SOURCE_ORDINAL))
                .build();

        assertEquals(NVIDIA_BASE_URL, config.getConfigValue(PRIMARY_BASE_URL).getValue());
        assertEquals(NVIDIA_BASE_URL, config.getConfigValue(EMBEDDING_BASE_URL).getValue());
    }

    /**
     * The NVIDIA default must be a model the provider still serves. {@code nvidia/nv-embedqa-e5-v5}
     * was retired on 2026-08-25 and answers 410, which failed every turn, since active memory embeds
     * the query before each reply (#357). Its successor only produces 2048 dimensions.
     */
    @Test
    void nvidiaKindDefaultsToALiveEmbeddingModelAtItsNativeDimension() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new LlmKindConfigSourceFactory())
                .withSources(new PropertiesConfigSource(
                        Map.of("LLM_KIND", "nvidia"), "env", OVERRIDE_SOURCE_ORDINAL))
                .build();

        assertEquals("nvidia/nemotron-3-embed-1b",
                config.getConfigValue("qlawkus.embedding.model-name").getValue());
        assertEquals(2048, ProviderKind.NVIDIA.embeddingDimensions(),
                "the model rejects any other dimension, and the pgvector column is sized from this");
    }

    @Test
    void explicitNativePrimaryBaseUrlIsHonoredAndInheritedByEmbedding() {
        String wiremock = "http://localhost:8089/v1";
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new LlmKindConfigSourceFactory())
                // An explicit override (e.g. %test application.properties) out-ranks the factory.
                .withSources(new PropertiesConfigSource(
                        Map.of("LLM_KIND", "nvidia", PRIMARY_BASE_URL, wiremock),
                        "application.properties", OVERRIDE_SOURCE_ORDINAL))
                .build();

        assertEquals(wiremock, config.getConfigValue(PRIMARY_BASE_URL).getValue());
        assertEquals(wiremock, config.getConfigValue(EMBEDDING_BASE_URL).getValue());
    }

    @Test
    void friendlyLlmBaseUrlOverrideWins() {
        String localOllama = "http://localhost:11434/v1";
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new LlmKindConfigSourceFactory())
                .withSources(new PropertiesConfigSource(
                        Map.of("LLM_KIND", "nvidia", "LLM_BASE_URL", localOllama),
                        "env", OVERRIDE_SOURCE_ORDINAL))
                .build();

        assertEquals(localOllama, config.getConfigValue(PRIMARY_BASE_URL).getValue());
    }

    private static final String PRIMARY_API_KEY = "quarkus.langchain4j.openai.\"primary\".api-key";
    private static final String PRIMARY_CHAT_MODEL = "quarkus.langchain4j.openai.\"primary\".chat-model.model-name";
    private static final String SECONDARY_BASE_URL = "quarkus.langchain4j.openai.\"secondary\".base-url";
    private static final String SECONDARY_API_KEY = "quarkus.langchain4j.openai.\"secondary\".api-key";
    private static final String SECONDARY_CHAT_MODEL = "quarkus.langchain4j.openai.\"secondary\".chat-model.model-name";
    private static final String SECONDARY_ENABLED = "qlawkus.model.secondary-enabled";
    private static final String ULTRA = "nvidia/nemotron-3-ultra-550b-a55b";
    private static final String LIGHTNING = "nvidia/nemotron-3.5-lightning-30b-a3b";

    private static SmallRyeConfig configWith(Map<String, String> overrides) {
        return new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(new LlmKindConfigSourceFactory())
                .withSources(new PropertiesConfigSource(overrides, "env", OVERRIDE_SOURCE_ORDINAL))
                .build();
    }

    @Test
    void nvidiaKindUsesUltraAsPrimaryChatModel() {
        SmallRyeConfig config = configWith(Map.of("LLM_KIND", "nvidia"));

        assertEquals(ULTRA, config.getConfigValue(PRIMARY_CHAT_MODEL).getValue());
    }

    @Test
    void nvidiaKindEnablesLightningAsSecondaryOnTheSameEndpointWithThePrimarysKey() {
        SmallRyeConfig config = configWith(Map.of("LLM_KIND", "nvidia", "LLM_API_KEY", "nvapi-secret"));

        assertEquals("true", config.getConfigValue(SECONDARY_ENABLED).getValue());
        assertEquals(LIGHTNING, config.getConfigValue(SECONDARY_CHAT_MODEL).getValue());
        assertEquals(NVIDIA_BASE_URL, config.getConfigValue(SECONDARY_BASE_URL).getValue());
        assertEquals("nvapi-secret", config.getConfigValue(SECONDARY_API_KEY).getValue());
    }

    /**
     * The secondary key is a reference to the primary key property, not a copy: a key that reaches the
     * primary later and from a higher source (the keystore at ordinal 350 publishes the native
     * property) must reach the secondary too, with no second secret to store.
     */
    @Test
    void secondaryKeyFollowsTheNativePrimaryKeyFromAHigherSource() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(new LlmKindConfigSourceFactory())
                .withSources(new PropertiesConfigSource(
                        Map.of("LLM_KIND", "nvidia", "LLM_API_KEY", "from-env"), "env", OVERRIDE_SOURCE_ORDINAL))
                .withSources(new PropertiesConfigSource(
                        Map.of(PRIMARY_API_KEY, "from-keystore"), "keystore", 350))
                .build();

        assertEquals("from-keystore", config.getConfigValue(SECONDARY_API_KEY).getValue());
    }

    @Test
    void explicitSecondaryOverridesWinOverTheKindDefaults() {
        SmallRyeConfig config = configWith(Map.of(
                "LLM_KIND", "nvidia",
                "LLM_API_KEY", "nvapi-secret",
                "LLM_SECONDARY_CHAT_MODEL", "meta/llama-4-maverick",
                "LLM_SECONDARY_BASE_URL", "https://other.example/v1",
                "LLM_SECONDARY_API_KEY", "other-key"));

        assertEquals("meta/llama-4-maverick", config.getConfigValue(SECONDARY_CHAT_MODEL).getValue());
        assertEquals("https://other.example/v1", config.getConfigValue(SECONDARY_BASE_URL).getValue());
        assertEquals("other-key", config.getConfigValue(SECONDARY_API_KEY).getValue());
        assertEquals(ULTRA, config.getConfigValue(PRIMARY_CHAT_MODEL).getValue(),
                "overriding the secondary must not touch the primary");
    }

    @Test
    void aKindWithoutASameProviderModelDisablesTheSecondaryTier() {
        SmallRyeConfig config = configWith(Map.of("LLM_KIND", "mistral", "LLM_API_KEY", "mistral-secret"));

        assertEquals("false", config.getConfigValue(SECONDARY_ENABLED).getValue());
        assertEquals("no-key", config.getConfigValue(SECONDARY_API_KEY).getValue(),
                "the primary provider's secret must never be attached to a disabled tier");
    }

    @Test
    void settingASecondaryModelEnablesTheTierForAKindWithoutOne() {
        SmallRyeConfig config = configWith(Map.of(
                "LLM_KIND", "mistral",
                "LLM_API_KEY", "mistral-secret",
                "LLM_SECONDARY_CHAT_MODEL", "mistral-small-latest"));

        assertEquals("true", config.getConfigValue(SECONDARY_ENABLED).getValue());
        assertEquals("mistral-small-latest", config.getConfigValue(SECONDARY_CHAT_MODEL).getValue());
    }

    @Test
    void localOllamaKeepsTheNativeFallbackUntouched() {
        SmallRyeConfig config = configWith(Map.of(
                "LLM_BASE_URL", "http://ollama:11434/v1",
                "LLM_API_KEY", "dummy"));

        assertEquals("false", config.getConfigValue(SECONDARY_ENABLED).getValue());
        assertNull(config.getConfigValue("quarkus.langchain4j.openai.\"fallback\".base-url").getValue(),
                "the Ollama fallback is native and must not be emitted as an OpenAI-compatible model");
    }
}
