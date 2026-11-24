package dev.omatheusmesmo.qlawkus.cognition.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DuplicateJudgesTest {

  private static double count(SimpleMeterRegistry registry, String outcome) {
    var counter = registry.find(AgentMeters.TURN_GATE).tag("question", "duplicate-fact")
        .tag("outcome", outcome).counter();
    return counter == null ? 0.0 : counter.count();
  }

  @Test
  void aJudgeThatThrowsKeepsBothFactsBecauseARemovalCannotBeUndone() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    DuplicateJudges judges = new DuplicateJudges((kept, candidate) -> {
      throw new IllegalStateException("decision model unavailable");
    }, new AgentMeters(registry));

    assertFalse(judges.sameFact("a", "b"), "a broken judge must never delete a fact");
    assertEquals(1.0, count(registry, AgentMeters.GATE_ERROR));
  }

  @Test
  void aConfirmedRewordingIsCountedAsPassAndADistinctPairAsSkip() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    assertTrue(new DuplicateJudges((kept, candidate) -> true, new AgentMeters(registry)).sameFact("a", "b"));
    assertFalse(new DuplicateJudges((kept, candidate) -> false, new AgentMeters(registry)).sameFact("a", "b"));

    assertEquals(1.0, count(registry, AgentMeters.GATE_PASS));
    assertEquals(1.0, count(registry, AgentMeters.GATE_SKIP));
  }

  @Test
  void theJudgeIsConsultedOnTheBatchWorkload() {
    AtomicReference<String> workload = new AtomicReference<>();
    new DuplicateJudges((kept, candidate) -> {
      workload.set(WorkloadContext.current());
      return true;
    }, AgentMeters.disabled()).sameFact("a", "b");

    assertEquals(WorkloadContext.BATCH, workload.get());
  }

  @Test
  void theDefaultJudgeKeepsTodaysBehaviorOfTrustingTheDistance() {
    assertTrue(new PassThroughDuplicateJudge().sameFact("a", "b"));
  }
}
