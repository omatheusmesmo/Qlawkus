package dev.omatheusmesmo.qlawkus.messaging.discord;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.messaging.discord")
public interface DiscordConfig {

    /**
     * Discord Bot Token from the Developer Portal. Leave unset to disable Discord provider.
     */
    Optional<String> botToken();

    /**
     * Discord Application ID, used for slash command registration.
     */
    Optional<String> applicationId();

    /**
     * Whether the bot responds to every message in the guild channels it is in. When false, the bot stays quiet in channels and answers only direct messages and slash commands. Direct messages are governed separately by {@code respond-to-direct-messages}.
     */
    @WithDefault("true")
    boolean respondToAllMessages();

    /**
     * Whether the bot responds to direct messages. Kept separate from {@code respond-to-all-messages} so quieting the bot in shared channels does not also cut off the owner's private conversation with it; set both to false to leave only slash commands.
     */
    @WithDefault("true")
    boolean respondToDirectMessages();

    /**
     * Channel ID where the bot posts a greeting message once Gateway connects. Useful for confirming the bot is online and reachable. Leave unset to disable.
     */
    Optional<String> startupChannelId();

    /**
     * Guild (server) ID where slash commands should be registered. When set, commands are registered guild-specific (instant availability). When unset, commands are registered globally (up to 1 hour propagation).
     */
    Optional<String> guildId();

    /**
     * Greeting text posted to the startup channel on Gateway connection.
     */
    @WithDefault("👋 Qlawkus online and listening")
    String startupGreeting();
}
