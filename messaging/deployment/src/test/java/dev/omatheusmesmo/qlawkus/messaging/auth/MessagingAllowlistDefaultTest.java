package dev.omatheusmesmo.qlawkus.messaging.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MessagingAllowlistDefaultTest {

    @Test
    void emptyEnvironmentValueBootsAndGrantsNobody() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withMapping(MessagingAuthConfig.class)
                .withSources(new PropertiesConfigSource(Map.of(
                        "qlawkus.messaging.auth.allowed-users.telegram", "${TELEGRAM_ALLOWED_USER_IDS:}",
                        "TELEGRAM_ALLOWED_USER_IDS", ""), "test", 100))
                .build();

        Optional<List<String>> telegram = config.getConfigMapping(MessagingAuthConfig.class).allowedUsers()
                .getOrDefault("telegram", Optional.empty());
        assertTrue(telegram.map(List::isEmpty).orElse(true), "empty env must not grant anyone: " + telegram);
    }
}
