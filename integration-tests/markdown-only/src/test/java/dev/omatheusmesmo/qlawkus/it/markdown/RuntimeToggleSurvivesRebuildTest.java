package dev.omatheusmesmo.qlawkus.it.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;

/**
 * Proves the external override survives a rebuild of the artifact (#73). The override lives on the
 * filesystem, not in the packaged jar, so changing the baked-in manifest - here simulated by pointing
 * the baked manifest at a classpath resource that no longer exists - must not disturb it: the
 * overridden toggle still resolves from {@code agent.runtime.yml}, while the baked-only toggle
 * disappears with the artifact.
 */
@QuarkusTest
@TestProfile(RuntimeToggleSurvivesRebuildTest.RebuiltWithoutBakedManifest.class)
class RuntimeToggleSurvivesRebuildTest {

    @Inject
    Config config;

    @Test
    void externalOverrideOutlivesTheBakedManifest() {
        assertEquals("override", config.getValue("qlawkus.rt.contested", String.class),
                "the external override is filesystem-backed and survives the rebuild");
        assertTrue(config.getOptionalValue("qlawkus.rt.baked-only", String.class).isEmpty(),
                "the baked-only toggle vanishes with the artifact it lived in");
    }

    public static class RebuiltWithoutBakedManifest implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("qlawkus.runtime.manifest", "qlawkus/removed-by-rebuild.yml");
        }
    }
}
