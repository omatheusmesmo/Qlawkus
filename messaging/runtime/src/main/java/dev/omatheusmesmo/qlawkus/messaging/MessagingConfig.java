package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.agent.ToolProgress;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Map;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.messaging")
public interface MessagingConfig {

    /**
     * Per-provider configuration keyed by provider ID (e.g. telegram, discord).
     */
    Map<String, ProviderConfig> provider();

    /**
     * Tool activity shown in a status message while a turn runs.
     */
    ToolProgressConfig toolProgress();

    /**
     * Tool-progress settings. {@code /verbose} changes the mode of one chat in memory only.
     */
    interface ToolProgressConfig {

        /**
         * What each chat sees of a tool call: {@code off} shows nothing, {@code status} the tool name
         * and duration, {@code preview} adds the start of its arguments, {@code verbose} a longer part.
         */
        @WithDefault("preview")
        ToolProgress mode();

        /**
         * What replaces the status message when the reply arrives: {@code summary} edits it to one
         * line such as "7 tools, 4.2s", {@code delete} removes it.
         */
        @WithDefault("summary")
        String onComplete();

        /**
         * Lines kept in the status message; older calls scroll off and a count of them remains.
         */
        @WithDefault("6")
        int maxLines();
    }

    /**
     * Configuration for a single messaging provider.
     */
    interface ProviderConfig {

        /**
         * Whether this provider is active. Defaults to true.
         */
        @WithDefault("true")
        boolean enabled();
    }
}
