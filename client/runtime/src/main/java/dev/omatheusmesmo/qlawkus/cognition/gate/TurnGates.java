package dev.omatheusmesmo.qlawkus.cognition.gate;

import dev.langchain4j.data.message.ChatMessage;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * The single place the post-turn observers consult a {@link TurnGate}, and the owner of its policy.
 *
 * <p>The policy is fail-open: when the gate throws, the turn counts as worth its generation, which is
 * exactly what happens without a gate. A gate exists to save work, so its failure must never silently
 * drop the work it was guarding. The failure is logged by the exception's class only, since the
 * exception message can carry part of the conversation, and it is counted as {@code error}.
 */
@ApplicationScoped
public class TurnGates {

  private final TurnGate gate;
  private final AgentMeters meters;

  @Inject
  public TurnGates(TurnGate gate, AgentMeters meters) {
    this.gate = gate;
    this.meters = meters;
  }

  public boolean worthIt(TurnQuestion question, List<ChatMessage> messages) {
    try {
      boolean worthIt = WorkloadContext.callAs(WorkloadContext.BATCH, () -> gate.worthIt(question, messages));
      meters.turnGate(question.id(), worthIt ? AgentMeters.GATE_PASS : AgentMeters.GATE_SKIP);
      return worthIt;
    } catch (Exception e) {
      Log.warnf("Turn gate failed for %s (%s); running the generation anyway",
          question.id(), e.getClass().getSimpleName());
      meters.turnGate(question.id(), AgentMeters.GATE_ERROR);
      return true;
    }
  }
}
