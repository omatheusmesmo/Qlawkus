package dev.omatheusmesmo.qlawkus.model;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
