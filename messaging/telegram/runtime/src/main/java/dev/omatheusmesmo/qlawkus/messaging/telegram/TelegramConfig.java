package dev.omatheusmesmo.qlawkus.messaging.telegram;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.messaging.telegram")
public interface TelegramConfig {

    /**
     * Telegram Bot API token obtained from BotFather.
     * Format: {bot_id}:{secret}. Absent disables outbound Telegram delivery.
     */
    Optional<String> botToken();

    /**
     * Base URL of the Telegram Bot API. Used when building file download URLs for
     * {@link dev.omatheusmesmo.qlawkus.messaging.MediaDownloader}.
     */
    @WithDefault("https://api.telegram.org")
    String apiBaseUrl();

    /**
     * Ingestion mode: "polling" (getUpdates loop, works behind NAT, default) or
     * "webhook" (Telegram posts to /api/webhook/telegram, needs a public URL).
     */
    @WithDefault("polling")
    String mode();

    /** Public HTTPS URL registered with Telegram on startup in webhook mode. */
    Optional<String> webhookUrl();

    /**
     * Telegram webhook shared secret. Use 1–256 letters, digits, underscores or hyphens.
     * When set, every webhook update must carry the matching header. Document and photo webhooks
     * require this secret, including when the webhook is registered outside Qlawkus.
     */
    Optional<String> webhookSecretToken();
}
