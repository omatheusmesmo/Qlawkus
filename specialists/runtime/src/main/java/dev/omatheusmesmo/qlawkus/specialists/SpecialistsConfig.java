package dev.omatheusmesmo.qlawkus.specialists;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.specialists")
public interface SpecialistsConfig {

    /** Model tiers permitted for specialist runs and registration. The default uses the local fallback. */
    @WithDefault("fallback")
    List<Tier> allowedModels();

    /** Deadline in seconds shared by all workers of a run. */
    @WithDefault("45")
    @Min(1)
    @Max(45)
    int timeoutSeconds();

    /** Requested output token ceiling for each specialist call. */
    @WithDefault("256")
    @Min(16)
    @Max(256)
    int maxOutputTokens();

    enum Tier {
        FALLBACK, PRIMARY, SECONDARY
    }
}
