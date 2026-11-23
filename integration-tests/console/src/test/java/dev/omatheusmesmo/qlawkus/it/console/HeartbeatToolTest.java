package dev.omatheusmesmo.qlawkus.it.console;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.heartbeat.HeartbeatTool;
import dev.omatheusmesmo.qlawkus.task.OwnerChatDirectory;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class HeartbeatToolTest {

    @Inject
    @QlawTool(capturePayload = false)
    HeartbeatTool tool;

    @Inject
    AgentDeliveryContext delivery;

    @Inject
    OwnerChatDirectory chats;

    @Test
    void requiresTheConfiguredPrincipalOrMostRecentlyAcceptedOwnerChat() {
        try {
            assertDenied();
            delivery.restore(new AgentDeliveryContext.Snapshot("fixture", "rest", null, "other"));
            assertDenied();

            delivery.restore(new AgentDeliveryContext.Snapshot("fixture", "rest", null, "qlawkus"));
            assertConfigured();

            chats.remember("heartbeat-test", "accepted-test-chat");
            delivery.set("fixture", "heartbeat-test", "different-test-chat");
            assertDenied();
            delivery.set("fixture", "heartbeat-test", "accepted-test-chat");
            assertConfigured();
        } finally {
            delivery.clear();
        }
    }

    private void assertDenied() {
        assertTrue(tool.heartbeatStatus().contains("authorized owner conversation"));
        assertTrue(tool.configureHeartbeat(false, 5, "Inspect schedules.", null).contains("authorized owner conversation"));
    }

    private void assertConfigured() {
        assertTrue(tool.configureHeartbeat(false, 5, "Inspect schedules.", null).contains("Heartbeat disabled"));
    }
}
