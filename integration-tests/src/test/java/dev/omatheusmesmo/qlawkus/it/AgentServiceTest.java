package dev.omatheusmesmo.qlawkus.it;

import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.cognition.Soul;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@Execution(ExecutionMode.SAME_THREAD)
class AgentServiceTest {

  @Inject
  AgentService agentService;

  @AfterEach
  @Transactional
  void resetSoul() {
    SoulResetHelper.resetToDefaults();
  }

  @Test
  void agentService_isInjectable() {
    assertNotNull(agentService);
  }

  @Test
  void chat_returnsResponseWithSoulIdentity() {
    assertEquals("Qlawkus", currentName());

    String response = agentService.chat("What is your name? Reply with just your name, nothing else. Do not use any tools.")
      .collect()
      .in(StringBuilder::new, StringBuilder::append)
      .await()
                .atMost(Duration.ofSeconds(300))
                .toString();
            assertNotNull(response);
            assertFalse(response.isBlank(), "LLM should return a non-blank response");
            assertTrue(response.toLowerCase().contains("qlawkus"),
                "Response should contain the soul name 'Qlawkus'. Got: " + response);
    }

    @Test
    void chat_usesToolToChangeOwnName() {
        String response = agentService.chat("Change your name to Nova. Use your available tools to do it.")
                .collect()
                .in(StringBuilder::new, StringBuilder::append)
                .await()
                .atMost(Duration.ofSeconds(300))
      .toString();
    assertNotNull(response);
    assertFalse(response.isBlank(), "LLM should return a non-blank response");

    assertEquals("Nova", currentName(),
      "LLM should have used the updateName tool to change the soul name to 'Nova'.");
  }

  @Transactional
  String currentName() {
    return Soul.findSoul().name;
  }
}
