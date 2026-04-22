package dev.omatheusmesmo.qlawkus.agent;

import dev.omatheusmesmo.qlawkus.cognition.Soul;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
class AgentServiceTest {

  @Inject
  AgentService agentService;

  @Test
  @Order(1)
  void agentService_isInjectable() {
    assertNotNull(agentService);
  }

  @Test
  @Order(2)
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
  @Order(3)
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

    resetSoulName();
  }

  @Transactional
  String currentName() {
    return Soul.findSoul().name;
  }

  @Transactional
  void resetSoulName() {
    Soul.findSoul().rename("Qlawkus");
  }
}
