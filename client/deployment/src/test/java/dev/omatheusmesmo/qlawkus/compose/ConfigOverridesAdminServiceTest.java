package dev.omatheusmesmo.qlawkus.compose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.config.InvalidConfigOverrideException;
import dev.omatheusmesmo.qlawkus.config.metadata.ConfigMetadataIndex;
import dev.omatheusmesmo.qlawkus.dto.ConfigOverridesState;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Uses the real {@link ConfigMetadataIndex} (built from whatever {@code quarkus-config-doc} metadata
 * is on the test classpath), so these assertions exercise real, documented properties from
 * {@code client} rather than synthetic ones.
 */
class ConfigOverridesAdminServiceTest {

    private static final ConfigMetadataIndex METADATA = new ConfigMetadataIndex();

    // qlawkus.composition.manifest is BUILD_TIME; qlawkus.memory-curation.enabled is RUN_TIME.
    private static final String BUILD_TIME_PROPERTY = "qlawkus.composition.manifest";
    private static final String RUN_TIME_PROPERTY = "qlawkus.memory-curation.enabled";

    private ConfigOverridesAdminService serviceIn(Path dir) {
        return new ConfigOverridesAdminService(dir.resolve("config.staged.properties"), METADATA);
    }

    @Test
    void stagesAValidDocumentAndExposesIt(@TempDir Path dir) throws IOException {
        ConfigOverridesAdminService service = serviceIn(dir);
        String doc = BUILD_TIME_PROPERTY + "=qlawkus/agent.yml\n";

        service.stage(doc);

        assertTrue(Files.exists(dir.resolve("config.staged.properties")), "staged file must be written");
        ConfigOverridesState state = service.currentState();
        assertEquals(doc, state.staged(), "GET must reflect the staged document verbatim");
        assertNotNull(state.stagedAt(), "a staged document carries a timestamp");
    }

    @Test
    void rejectsAnUndocumentedProperty(@TempDir Path dir) {
        ConfigOverridesAdminService service = serviceIn(dir);

        assertThrows(InvalidConfigOverrideException.class,
                () -> service.stage("qlawkus.does-not-exist.nope=x\n"));
    }

    @Test
    void rejectsARunTimeProperty(@TempDir Path dir) {
        ConfigOverridesAdminService service = serviceIn(dir);

        assertThrows(InvalidConfigOverrideException.class,
                () -> service.stage(RUN_TIME_PROPERTY + "=false\n"));
    }

    @Test
    void rejectsASecretProperty(@TempDir Path dir) {
        ConfigOverridesAdminService service = serviceIn(dir);

        assertThrows(InvalidConfigOverrideException.class,
                () -> service.stage("qlawkus.admin.password-hash=x\n"));
    }

    @Test
    void discardsAStagedDocument(@TempDir Path dir) throws IOException {
        ConfigOverridesAdminService service = serviceIn(dir);
        service.stage(BUILD_TIME_PROPERTY + "=qlawkus/agent.yml\n");

        assertTrue(service.discardStaged(), "discarding a present document returns true");
        assertFalse(service.discardStaged(), "discarding when none is staged returns false");
        assertNull(service.currentState().staged(), "state shows no staged document after discard");
    }

    @Test
    void stageOneAddsASinglePropertyPreservingOthers(@TempDir Path dir) throws IOException {
        ConfigOverridesAdminService service = serviceIn(dir);

        service.stageOne(BUILD_TIME_PROPERTY, "qlawkus/agent.yml");

        assertEquals("qlawkus/agent.yml", stagedProperties(service).getProperty(BUILD_TIME_PROPERTY));
    }

    private static Properties stagedProperties(ConfigOverridesAdminService service) throws IOException {
        Properties properties = new Properties();
        try (StringReader reader = new StringReader(service.currentState().staged())) {
            properties.load(reader);
        }
        return properties;
    }

    @Test
    void discardOneRemovesASinglePropertyAndDeletesFileWhenEmpty(@TempDir Path dir) throws IOException {
        ConfigOverridesAdminService service = serviceIn(dir);
        service.stageOne(BUILD_TIME_PROPERTY, "qlawkus/agent.yml");

        assertTrue(service.discardOne(BUILD_TIME_PROPERTY));
        assertNull(service.currentState().staged(), "removing the only staged key discards the file");
        assertFalse(service.discardOne(BUILD_TIME_PROPERTY), "discarding an absent key returns false");
    }
}
