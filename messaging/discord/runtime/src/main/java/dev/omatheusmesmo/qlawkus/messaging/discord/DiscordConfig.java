package dev.omatheusmesmo.qlawkus.messaging.discord;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.messaging.discord")
public interface DiscordConfig {

    /**
     * Discord Bot Token obtained from the Discord Developer Portal.
     */
    String botToken();

    /**
     * Discord Application ID.
     */
    String applicationId();
}
