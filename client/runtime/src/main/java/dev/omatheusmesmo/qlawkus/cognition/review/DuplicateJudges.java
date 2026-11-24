package dev.omatheusmesmo.qlawkus.cognition.review;

import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The single place the memory review consults a {@link DuplicateJudge}, and the owner of its policy.
 *
 * <p>The policy is fail-safe: when the judge throws, the pair counts as distinct and nothing is
 * deleted. Each answer is counted on the turn-gate meter under the question {@code duplicate-fact}:
 * {@code pass} is a confirmed rewording (the fact goes), {@code skip} a pair kept, {@code error} a
 * judge that failed. The failure is logged by exception class only, since the message can carry a
 * fact.
 */
@ApplicationScoped
public class DuplicateJudges {

  static final String QUESTION = "duplicate-fact";

  private final DuplicateJudge judge;
  private final AgentMeters meters;

  @Inject
  public DuplicateJudges(DuplicateJudge judge, AgentMeters meters) {
    this.judge = judge;
    this.meters = meters;
  }

  public boolean sameFact(String kept, String candidate) {
    try {
      boolean same = WorkloadContext.callAs(WorkloadContext.BATCH, () -> judge.sameFact(kept, candidate));
      meters.turnGate(QUESTION, same ? AgentMeters.GATE_PASS : AgentMeters.GATE_SKIP);
      return same;
    } catch (Exception e) {
      Log.warnf("Duplicate judge failed (%s); keeping both facts", e.getClass().getSimpleName());
      meters.turnGate(QUESTION, AgentMeters.GATE_ERROR);
      return false;
    }
  }
}
