package dev.omatheusmesmo.qlawkus.tools.google.gmail;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "qlawkus.google.gmail")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface GoogleGmailConfig {

    /** Whether the Gmail tool is enabled. */
    @WithDefault("false")
    boolean enabled();

    /** Gmail user identifier. Use {@code me} for the authenticated user. */
    @WithDefault("me")
    String userId();

    /** Default maximum number of messages returned per request. */
    @WithDefault("10")
    int maxResults();
}
