package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.startup-thought")
public interface StartupConfig {

    /**
     * Whether to generate a startup thought on application boot.
     */
    @WithDefault("true")
    boolean enabled();
}
