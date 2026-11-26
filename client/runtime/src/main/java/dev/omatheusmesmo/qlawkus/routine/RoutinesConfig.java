package dev.omatheusmesmo.qlawkus.routine;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;

/**
 * The owner's recurring routines. Both are off until the owner turns them on, and each one writes to
 * the chat the owner last wrote from.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.routines")
public interface RoutinesConfig {

    /** The Friday report: what the owner did this week. */
    Weekly weekly();

    /** The Monday briefing: this week's meetings and the pull requests that look abandoned. */
    Morning morning();

    interface Weekly {

        /**
         * Whether the weekly report runs. Read on every firing, so it can be switched without a restart.
         */
        @WithDefault("false")
        boolean enabled();

        /**
         * Cron expression of the weekly report, in the zone below. Read at startup, so changing it needs a
         * restart.
         */
        @WithDefault("0 0 16 ? * FRI")
        String cron();

        /**
         * IANA time zone the cron is read in. Defaults to the agent's own time zone,
         * {@code qlawkus.agent.timezone}. Read at startup.
         */
        @WithDefault("${qlawkus.agent.timezone}")
        String timeZone();

        /**
         * What to ask the agent instead of the built-in instruction. The date, the reply contract and the
         * rule that nothing is recorded without the owner's answer still apply.
         */
        Optional<String> prompt();
    }

    interface Morning {

        /**
         * Whether the Monday briefing runs. Read on every firing, so it can be switched without a restart.
         */
        @WithDefault("false")
        boolean enabled();

        /**
         * Cron expression of the Monday briefing, in the zone below. Read at startup, so changing it needs
         * a restart.
         */
        @WithDefault("0 0 8 ? * MON")
        String cron();

        /**
         * IANA time zone the cron is read in. Defaults to the agent's own time zone,
         * {@code qlawkus.agent.timezone}. Read at startup.
         */
        @WithDefault("${qlawkus.agent.timezone}")
        String timeZone();

        /**
         * Days without any activity after which an open pull request counts as abandoned.
         */
        @WithDefault("14")
        int abandonedAfterDays();

        /**
         * What to ask the agent instead of the built-in instruction. The date and the reply contract still
         * apply, and the abandoned-after threshold is still stated.
         */
        Optional<String> prompt();
    }
}
