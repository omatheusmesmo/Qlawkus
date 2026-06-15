package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.memory-review")
public interface MemoryReviewConfig {

    /**
     * Cosine similarity above which two facts are treated as near-duplicates and the redundant one
     * is removed by the nightly memory-review job. Higher is stricter (removes fewer facts).
     */
    @WithDefault("0.97")
    double similarityThreshold();

    /**
     * Cron expression for the scheduled memory-review job.
     */
    @WithDefault("0 30 3 * * ?")
    String cron();
}
