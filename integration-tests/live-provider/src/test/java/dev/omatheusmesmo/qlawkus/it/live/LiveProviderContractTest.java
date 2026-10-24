package dev.omatheusmesmo.qlawkus.it.live;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.omatheusmesmo.qlawkus.model.PrimaryEmbedding;
import dev.omatheusmesmo.qlawkus.model.ProviderKind;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Calls the real provider once per surface. Every other embedding and chat test in the reactor runs
 * against WireMock, so a model the provider retires keeps them green: NVIDIA retired
 * {@code nv-embedqa-e5-v5} on 2026-08-25 and the suite did not notice for two months (#359).
 *
 * <p>It runs only where a real key is present ({@code QlawkusTestUtils#usesLLM}) and skips cleanly
 * everywhere else, so it needs the {@code LLM_*} environment a deployment would have: at least
 * {@code LLM_API_KEY}, plus {@code LLM_KIND} and the model overrides when the provider is not the
 * default. The models are called directly rather than through the fallback chain, since a call that
 * silently landed on the secondary provider would pass without proving anything about the primary.
 * Inputs are a few tokens and there is no agent loop, so a run costs next to nothing.
 */
@QuarkusTest
@EnabledIf("dev.omatheusmesmo.qlawkus.testing.QlawkusTestUtils#usesLLM")
class LiveProviderContractTest {

    @Inject
    @PrimaryEmbedding
    EmbeddingModel embeddingModel;

    @Inject
    @ModelName("primary")
    ChatModel chatModel;

    @Inject
    Config config;

    @ConfigProperty(name = "qlawkus.embedding.model-name")
    String embeddingModelName;

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void embeddingEndpointAnswersWithTheConfiguredDimension() {
        float[] vector = embeddingModel.embed("qlawkus provider contract").content().vector();

        assertNotNull(vector);
        assertEquals(expectedDimension(), vector.length,
                "model '" + embeddingModelName + "' answered with another dimension than configured");
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void chatEndpointAnswersAMinimalPrompt() {
        String answer = chatModel.chat("Reply with the single word: pong");

        assertNotNull(answer);
        assertFalse(answer.isBlank(), "the chat model answered with an empty message");
    }

    private int expectedDimension() {
        return config.getOptionalValue("EMBEDDING_DIMENSION", Integer.class)
                .orElseGet(() -> embeddingKind().embeddingDimensions());
    }

    private ProviderKind embeddingKind() {
        String kind = config.getOptionalValue("LLM_EMBEDDING_KIND", String.class)
                .or(() -> config.getOptionalValue("LLM_KIND", String.class))
                .orElse("openai");
        return ProviderKind.of(kind);
    }
}
