package dev.omatheusmesmo.qlawkus.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StartupSummaryTest {

    private static final StartupSummary.Settings SETTINGS = new StartupSummary.Settings(
            "NVIDIA", "nvidia/nemotron-3-ultra-550b-a55b", "nvidia/nemotron-3.5-lightning-30b-a3b", "qwen3:8b",
            "nvidia/nemotron-3-embed-1b", "2048", "pgvector", false, true, List.of("discord", "telegram"));

    @Test
    void theSummaryNamesTheModelsTheProviderAndTheEmbeddingDimension() {
        List<String> lines = StartupSummary.lines(SETTINGS);

        String models = lines.get(0);
        assertTrue(models.contains("provider=NVIDIA"), models);
        assertTrue(models.contains("primary=nvidia/nemotron-3-ultra-550b-a55b"), models);
        assertTrue(models.contains("secondary=nvidia/nemotron-3.5-lightning-30b-a3b"), models);
        assertTrue(models.contains("fallback=qwen3:8b"), models);
        String embeddings = lines.get(1);
        assertTrue(embeddings.contains("model=nvidia/nemotron-3-embed-1b"), embeddings);
        assertTrue(embeddings.contains("dimension=2048"), embeddings);
    }

    @Test
    void theSummaryNamesTheBackendCapabilitiesAndChannels() {
        List<String> lines = StartupSummary.lines(SETTINGS);

        String runtime = lines.get(2);
        assertTrue(runtime.contains("cognition=pgvector"), runtime);
        assertTrue(runtime.contains("self-observe=off"), runtime);
        assertTrue(runtime.contains("observability=on"), runtime);
        assertTrue(runtime.contains("channels=discord,telegram"), runtime);
    }

    @Test
    void absentSettingsAreReportedAsAbsentNotAsNull() {
        StartupSummary.Settings sparse = new StartupSummary.Settings(
                null, null, null, null, null, null, "markdown", true, false, List.of());

        String text = String.join("\n", StartupSummary.lines(sparse));

        assertFalse(text.contains("null"), text);
        assertTrue(text.contains("channels=none"), text);
        assertTrue(text.contains("self-observe=on"), text);
        assertEquals(3, StartupSummary.lines(sparse).size());
    }
}
