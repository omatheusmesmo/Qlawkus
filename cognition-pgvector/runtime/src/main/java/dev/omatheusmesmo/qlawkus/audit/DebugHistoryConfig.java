package dev.omatheusmesmo.qlawkus.audit;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Local PostgreSQL history for diagnosing agent and scheduler execution. */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.debug-history")
public interface DebugHistoryConfig {
    /** Record bounded execution metadata in the PostgreSQL history. */
    @WithDefault("false")
    boolean enabled();

    /** Keep short redacted owner messages and model reply excerpts locally. */
    @WithDefault("false")
    boolean captureText();

    /** Days to retain execution metadata, clamped to 1–90 days. */
    @WithDefault("14")
    int retentionDays();
}
