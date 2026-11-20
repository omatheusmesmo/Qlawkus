package dev.omatheusmesmo.qlawkus.messaging.auth;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

import java.util.Map;
import java.util.Optional;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.messaging.auth")
public interface MessagingAuthConfig {

    /**
     * Allowed user identifiers per provider (e.g. chat_id for Telegram, user_id for Discord).
     * Key is provider ID, value is a comma-separated list of allowed identifiers. A provider with no
     * entry, or an empty value, rejects every sender; the entry {@code *} allows everyone. The value is optional
     * because an empty environment variable must read as absent: SmallRye rejects an empty value for
     * a plain list at boot. Mapping the raw string also lets SmallRye track these dynamic keys as used.
     * Example: qlawkus.messaging.auth.allowed-users.telegram=123456789,987654321
     */
    Map<String, Optional<String>> allowedUsers();
}
