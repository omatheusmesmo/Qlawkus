package dev.omatheusmesmo.qlawkus.messaging.auth;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.messaging.auth")
public interface MessagingAuthConfig {

    /**
     * Allowed user identifiers per provider (e.g. chat_id for Telegram, user_id for Discord).
     * Key is provider ID, value is the list of allowed identifiers. A provider with no entry, or an
     * empty list, rejects every sender; the entry {@code *} allows everyone. The value is optional
     * because an empty environment variable must read as absent: SmallRye rejects an empty value for
     * a plain list at boot.
     * Example: qlawkus.messaging.auth.allowed-users.telegram=123456789,987654321
     */
    Map<String, Optional<List<String>>> allowedUsers();
}
