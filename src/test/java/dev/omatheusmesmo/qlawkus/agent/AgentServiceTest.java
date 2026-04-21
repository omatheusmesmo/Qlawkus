package dev.omatheusmesmo.qlawkus.agent;

import dev.omatheusmesmo.qlawkus.cognition.SoulEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AgentServiceTest {

    @Inject
    AgentService agentService;

    @Test
    void agentService_isInjectable() {
        assertNotNull(agentService);
    }

    @Test
    @Transactional
    void chat_returnsResponseWithSoulIdentity() {
        SoulEntity soul = SoulEntity.findSoul();
        String soulName = soul.name.toLowerCase();

        String response = agentService.chat("What is your name? You must include your exact name in your reply.");
        assertNotNull(response);
        assertFalse(response.isBlank());
        assertTrue(response.toLowerCase().contains(soulName),
                "Response should contain the soul name '" + soulName + "'. Got: " + response);
    }
}
