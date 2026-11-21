package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Shared model-assisted language selection for all interactive entry points. */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.reply-language")
public interface ReplyLanguageConfig {
    /**
     * Ask the already registered chat model to select the reply language once per turn. Its existing
     * provider/fallback chain is reused. Disabled distributions rely on the shared prompt policy;
     * the reference app enables classification for both text and voice.
     */
    @WithDefault("false")
    boolean enabled();
}
