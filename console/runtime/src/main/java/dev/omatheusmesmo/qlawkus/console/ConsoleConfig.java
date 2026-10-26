package dev.omatheusmesmo.qlawkus.console;

import dev.omatheusmesmo.qlawkus.agent.ToolProgress;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;

/**
 * Runtime configuration of the admin console. Owned by the optional {@code qlawkus-console}
 * extension; absent from a distribution that does not ship it.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.console")
public interface ConsoleConfig {

    /**
     * Browser-reachable base URL of Grafana (for example {@code http://localhost:3000}), not the
     * in-network address the agent uses. When set, the overview shows an Observability card linking
     * the Qlawkus dashboard and Grafana Explore on Prometheus, Tempo and Loki, which are all reached
     * through Grafana. Only a plain http or https URL is accepted: anything else is ignored with a
     * warning. When unset, the console shows no links.
     */
    Optional<String> grafanaUrl();

    /**
     * The chat page.
     */
    Chat chat();

    interface Chat {

        /**
         * How much of the agent's tool activity the chat page shows while a turn runs. {@code off}
         * shows only a "N tools, Xs" summary line, {@code status} shows each tool's name and duration,
         * {@code preview} adds the first 40 characters of its arguments, and {@code verbose} adds the
         * arguments (up to 300 characters) in a collapsible detail. Tool results are never shown. The
         * messaging channels are meant to take the same four names.
         */
        @WithDefault("preview")
        ToolProgress toolProgress();
    }
}
