package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.memory-curation")
public interface MemoryCurationConfig {

    /**
     * Whether the nightly curation job folds remembered facts into the owner profile.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Maximum number of facts loaded from the store and fed to the model in a single curation pass.
     */
    @WithDefault("200")
    int maxFacts();

    /**
     * Cron expression for the scheduled memory-curation job.
     */
    @WithDefault("0 45 3 * * ?")
    String cron();
}
