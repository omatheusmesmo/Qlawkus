package dev.omatheusmesmo.qlawkus.messaging.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MessagingAllowlistBootTest {
    @RegisterExtension
    static final QuarkusExtensionTest app = new QuarkusExtensionTest()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideConfigKey("quarkus.management.test-port", "0")
            .overrideRuntimeConfigKey("LLM_API_KEY", "test-key")
            .overrideRuntimeConfigKey("qlawkus.startup-thought.enabled", "false")
            .overrideRuntimeConfigKey("qlawkus.agent.state.root", "target/allowlist-boot/state")
            .overrideRuntimeConfigKey("qlawkus.agent.facts.root", "target/allowlist-boot/facts")
            .overrideRuntimeConfigKey("qlawkus.agent.episodic.root", "target/allowlist-boot/journals")
            .overrideRuntimeConfigKey("qlawkus.agent.working-memory.root", "target/allowlist-boot/working-memory")
            .overrideRuntimeConfigKey("qlawkus.skills.roots", "target/allowlist-boot/skills")
            .overrideRuntimeConfigKey("qlawkus.messaging.auth.allowed-users.telegram", "123456,987654")
            .overrideRuntimeConfigKey("qlawkus.messaging.auth.allowed-users.discord", "${EMPTY_ALLOWLIST:}")
            .setLogRecordPredicate(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
            .assertLogRecords(records -> {
                for (var record : records) {
                    String message = record.getMessage() + " " + Arrays.toString(record.getParameters());
                    assertFalse(message.contains("Unrecognized configuration") && message.contains("qlawkus.messaging.auth"),
                            message);
                }
            });

    @Inject
    MessagingAuthService auth;

    @Test
    void recognizesAllowlistPropertiesAndEnforcesThem() {
        assertTrue(auth.isAuthorized(MessagingMessage.text("telegram", "chat", "123456", "hello")));
        assertTrue(auth.isAuthorized(MessagingMessage.text("telegram", "chat", "987654", "hello")));
        assertFalse(auth.isAuthorized(MessagingMessage.text("telegram", "chat", "other", "hello")));
        assertFalse(auth.isAuthorized(MessagingMessage.text("discord", "chat", "123456", "hello")));
    }
}
