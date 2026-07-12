package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.consolidator")
public interface EpisodicConfig {

    /**
     * Cron expression for the scheduled episodic-consolidation job.
     */
    @WithDefault("0 0 3 * * ?")
    String cron();
}
