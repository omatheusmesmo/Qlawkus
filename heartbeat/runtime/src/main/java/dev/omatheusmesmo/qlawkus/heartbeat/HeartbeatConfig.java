package dev.omatheusmesmo.qlawkus.heartbeat;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Optional;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.heartbeat")
public interface HeartbeatConfig {

    String DEFAULT_PROMPT = "Check for missed or invalid scheduled tasks and whether curated memory is available. "
            + "Notify the owner only if scheduled tasks need attention and a destination is configured. "
            + "Otherwise record the observations without sending a message.";

    /** Minimum configurable interval in minutes. The hard minimum is one minute. */
    @WithDefault("1")
    @Min(1)
    @Max(HeartbeatSettings.MAX_INTERVAL_MINUTES)
    int minimumIntervalMinutes();

    /** Maximum number of model requests admitted each UTC day. */
    @WithDefault("48")
    @Min(1)
    @Max(1440)
    int maximumDailyRuns();

    /** Maximum reported input and output token total accepted for an individual decision. */
    @WithDefault("1600")
    @Min(1)
    @Max(32768)
    int maximumDecisionTokens();

    /** Overall deadline for a run, in seconds, including its observation and notification. */
    @WithDefault("15")
    @Min(1)
    @Max(30)
    int deadlineSeconds();

    /** Enables heartbeat on a new installation. Persisted settings take precedence after bootstrap. */
    @WithDefault("true")
    boolean enabled();

    /** Initial interval in minutes for a new installation. */
    @WithDefault("30")
    @Min(1)
    @Max(HeartbeatSettings.MAX_INTERVAL_MINUTES)
    int intervalMinutes();

    /** Initial owner instructions for a new installation. */
    @WithDefault(DEFAULT_PROMPT)
    Optional<String> prompt();

    /** Initial notification provider, which must already have an accepted owner conversation. */
    Optional<String> provider();
}
