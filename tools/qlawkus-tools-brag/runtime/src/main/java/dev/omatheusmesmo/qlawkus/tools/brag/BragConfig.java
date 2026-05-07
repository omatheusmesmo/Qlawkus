package dev.omatheusmesmo.qlawkus.tools.brag;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "qlawkus.brag")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface BragConfig {

    /**
     * Whether the Brag Document tool is enabled.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Whether LLM-based impact translation is enabled.
     * When disabled, achievements are stored with raw descriptions.
     */
    @WithDefault("true")
    boolean impactTranslationEnabled();

    /**
     * Number of days after which soft-deleted entries are purged.
     */
    @WithDefault("7")
    int cleanupAgeDays();
}
