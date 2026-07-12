package dev.omatheusmesmo.qlawkus.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.config.metadata.ConfigMetadataIndex;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Uses the real {@link ConfigMetadataIndex} (built from whatever {@code quarkus-config-doc} metadata
 * is on the test classpath), so these assertions exercise real, documented properties from
 * {@code client} rather than synthetic ones.
 */
class RuntimeToggleWriterTest {

    private static final ConfigMetadataIndex METADATA = new ConfigMetadataIndex();

    // qlawkus.memory-curation.enabled is RUN_TIME; qlawkus.composition.manifest is BUILD_TIME.
    private static final String RUN_TIME_PROPERTY = "qlawkus.memory-curation.enabled";
    private static final String BUILD_TIME_PROPERTY = "qlawkus.composition.manifest";

    private RuntimeToggleWriter writerIn(Path dir) {
        return new RuntimeToggleWriter(fakeConfig(dir.resolve("agent.runtime.yml")), METADATA);
    }

    private static RuntimeToggleConfig fakeConfig(Path overridePath) {
        return new RuntimeToggleConfig() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public String manifest() {
                return "qlawkus/agent.yml";
            }

            @Override
            public String overridePath() {
                return overridePath.toString();
            }

            @Override
            public int bakedOrdinal() {
                return 250;
            }

            @Override
            public int overrideOrdinal() {
                return 290;
            }
        };
    }

    @Test
    void setsAndPersistsARunTimeToggle(@TempDir Path dir) {
        RuntimeToggleWriter writer = writerIn(dir);

        writer.setToggle(RUN_TIME_PROPERTY, "false");

        assertEquals(Map.of(RUN_TIME_PROPERTY, "false"), writer.all());
    }

    @Test
    void settingASecondToggleDoesNotDropTheFirst(@TempDir Path dir) {
        RuntimeToggleWriter writer = writerIn(dir);

        writer.setToggle(RUN_TIME_PROPERTY, "false");
        writer.setToggle("qlawkus.memory-curation.max-facts", "50");

        assertEquals(Map.of(RUN_TIME_PROPERTY, "false", "qlawkus.memory-curation.max-facts", "50"),
                writer.all());
    }

    @Test
    void rejectsAnUndocumentedProperty(@TempDir Path dir) {
        RuntimeToggleWriter writer = writerIn(dir);

        assertThrows(IllegalArgumentException.class,
                () -> writer.setToggle("qlawkus.does-not-exist.nope", "x"));
    }

    @Test
    void rejectsABuildTimeProperty(@TempDir Path dir) {
        RuntimeToggleWriter writer = writerIn(dir);

        assertThrows(IllegalArgumentException.class,
                () -> writer.setToggle(BUILD_TIME_PROPERTY, "qlawkus/agent.yml"));
    }

    @Test
    void deletesAToggle(@TempDir Path dir) {
        RuntimeToggleWriter writer = writerIn(dir);
        writer.setToggle(RUN_TIME_PROPERTY, "false");

        assertTrue(writer.deleteToggle(RUN_TIME_PROPERTY));
        assertFalse(writer.deleteToggle(RUN_TIME_PROPERTY), "deleting an absent toggle returns false");
        assertTrue(writer.all().isEmpty());
    }
}
