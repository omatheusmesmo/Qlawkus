package dev.omatheusmesmo.qlawkus.it.cognition;

import dev.langchain4j.data.message.UserMessage;
import dev.omatheusmesmo.qlawkus.cognition.gate.TurnGates;
import dev.omatheusmesmo.qlawkus.cognition.gate.TurnQuestion;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With no custom {@code TurnGate} on the classpath, the seam must change nothing: every turn is
 * worth its post-turn generation, exactly as before the gate existed.
 */
@QuarkusTest
class TurnGatesDefaultTest {

  @Inject
  TurnGates turnGates;

  @Test
  void withoutACustomGateEveryTurnIsWorthIt() {
    TurnQuestion question = new TurnQuestion("durable-fact", "Does this exchange state a durable fact?");

    assertTrue(turnGates.worthIt(question, List.of(new UserMessage("hello"))));
  }
}
