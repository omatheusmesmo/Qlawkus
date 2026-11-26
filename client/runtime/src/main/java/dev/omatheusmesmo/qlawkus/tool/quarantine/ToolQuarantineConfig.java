package dev.omatheusmesmo.qlawkus.tool.quarantine;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;

/**
 * Runtime configuration for the tool quarantine: a tool that fails in a row is taken out of the
 * catalog the model sees, so a broken dependency stops costing a failed call on every turn.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.tool.quarantine")
public interface ToolQuarantineConfig {

    /**
     * Whether a tool that keeps failing is withheld from the model. Turn it off to keep every tool
     * available no matter how often it fails.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Failures in a row, with no success between them, that quarantine a tool. A call the model got
     * wrong (a bad argument, a path that does not exist) never counts, only a failure of the tool or
     * of what it depends on.
     */
    @WithDefault("5")
    int failureThreshold();

    /**
     * How long a quarantined tool stays out of the catalog. When it ends the tool comes back on
     * probation: the next failure quarantines it again straight away, the next success clears it.
     */
    @WithDefault("15m")
    Duration cooldown();
}
