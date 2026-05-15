package dev.omatheusmesmo.qlawkus.messaging.slack;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.messaging.slack")
public interface SlackConfig {

    /**
     * Slack Bot Token obtained from the Slack App configuration (starts with xoxb-).
     */
    String botToken();
}
