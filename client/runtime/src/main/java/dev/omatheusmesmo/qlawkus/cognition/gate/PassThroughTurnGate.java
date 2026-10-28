package dev.omatheusmesmo.qlawkus.cognition.gate;

import dev.langchain4j.data.message.ChatMessage;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * The default {@link TurnGate}: every turn is worth its generation, so the seam changes nothing until
 * a distribution supplies a real gate.
 */
@DefaultBean
@ApplicationScoped
public class PassThroughTurnGate implements TurnGate {

  @Override
  public boolean worthIt(TurnQuestion question, List<ChatMessage> messages) {
    return true;
  }
}
