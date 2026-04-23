package dev.omatheusmesmo.qlawkus.agent;

import dev.omatheusmesmo.qlawkus.cognition.Mood;
import dev.omatheusmesmo.qlawkus.cognition.Soul;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AgentServiceTest {

  @Inject
  AgentService agentService;

  @AfterEach
  @Transactional
  void resetSoul() {
    Soul soul = Soul.findSoul();
    soul.rename("Qlawkus");
    soul.shiftMood(Mood.FOCUSED);
    soul.shiftState("Awaiting first interaction. No active context or specialization yet.");
  }

  @Test
  void agentService_isInjectable() {
    assertNotNull(agentService);
  }

  @Test
  void chat_returnsResponseWithSoulIdentity() {
    assertEquals("Qlawkus", currentName());

    String response = agentService.chat("What is your name? You must include your exact name in your reply.")
        .collect()
        .in(StringBuilder::new, StringBuilder::append)
        .await()
        .indefinitely()
        .toString();
    assertNotNull(response);
    assertFalse(response.isBlank());
    assertTrue(response.toLowerCase().contains("qlawkus"),
        "Response should contain the soul name 'Qlawkus'. Got: " + response);
  }

  @Test
  void chat_usesToolToChangeOwnName() {
    String response = agentService.chat("Change your name to Nova. Use your available tools to do it.")
        .collect()
        .in(StringBuilder::new, StringBuilder::append)
        .await()
        .indefinitely()
        .toString();
    assertNotNull(response);
    assertFalse(response.isBlank());

    assertEquals("Nova", currentName(),
        "LLM should have used the updateName tool to change the soul name to 'Nova'.");
  }

  @Transactional
  String currentName() {
    return Soul.findSoul().name;
  }
}
