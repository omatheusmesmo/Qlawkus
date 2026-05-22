package dev.omatheusmesmo.qlawkus.messaging.telegram;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.messaging.telegram")
public interface TelegramConfig {

    /**
     * Telegram Bot API token obtained from BotFather.
     * Format: {bot_id}:{secret}
     */
    String botToken();

    /**
     * Base URL of the Telegram Bot API. Used for multipart uploads (sendAudio)
     * that bypass the REST client.
     */
    @WithDefault("https://api.telegram.org")
    String apiBaseUrl();
}
