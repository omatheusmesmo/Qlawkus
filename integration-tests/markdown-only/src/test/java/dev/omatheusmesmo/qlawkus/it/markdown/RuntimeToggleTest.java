package dev.omatheusmesmo.qlawkus.it.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

/**
 * Proves the runtime-toggle tier (#73) end to end in a database-free build: the baked-in
 * {@code runtime:} block of the classpath manifest supplies defaults, the external
 * {@code agent.runtime.yml} overrides them, and the two publish at ordinals that sit below env, so
 * {@code baked (250) < override (290) < env (300)}. Mirror of the secrets layering, opposite
 * intent - toggles yield to env/-D rather than outranking them. Also pins the leniency contract.
 */
@QuarkusTest
class RuntimeToggleTest {

    @Inject
    Config config;

    @Test
    void bakedDefaultApplies() {
        assertEquals("baked", config.getValue("qlawkus.rt.baked-only", String.class));
    }

    @Test
    void externalOverrideOutranksBakedDefault() {
        assertEquals("override", config.getValue("qlawkus.rt.contested", String.class));
    }

    @Test
    void ordinalChainIsBakedBelowOverrideBelowEnv() {
        assertEquals(250, ordinalOf("QlawkusRuntimeTogglesBaked"), "baked defaults publish at 250");
        int override = ordinalOf("QlawkusRuntimeTogglesOverride");
        assertEquals(290, override, "external override publishes at 290");
        assertTrue(override < 300, "external override must sit below the standard env ordinal (300)");
    }

    @Test
    void absentToggleIsLenient() {
        assertTrue(config.getOptionalValue("qlawkus.rt.missing", String.class).isEmpty());
    }

    private int ordinalOf(String sourceName) {
        for (ConfigSource source : config.getConfigSources()) {
            if (source.getName().contains(sourceName)) {
                return source.getOrdinal();
            }
        }
        throw new AssertionError("config source not found: " + sourceName);
    }
}
