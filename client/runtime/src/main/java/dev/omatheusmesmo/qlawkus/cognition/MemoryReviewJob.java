package dev.omatheusmesmo.qlawkus.cognition;

import dev.omatheusmesmo.qlawkus.config.MemoryReviewConfig;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Background self-review of long-term memory (inspired by Hermes' self-improvement loop). The
 * write-time MD5 dedup only catches byte-identical facts, so reworded variants of the same fact
 * accumulate over time. This job periodically removes semantic near-duplicates from the fact store,
 * keeping memory compact and reducing noise injected by active memory.
 */
@ApplicationScoped
public class MemoryReviewJob {
  @Inject
  AgentMeters meters;


  @Inject
  FactStore factStore;

  @Inject
  MemoryReviewConfig config;

  @Scheduled(identity = "memory-review", concurrentExecution = ConcurrentExecution.SKIP, cron = "{qlawkus.memory-review.cron:0 30 3 * * ?}")
  void review() {
    WorkloadContext.runAs(WorkloadContext.BATCH,
        () -> meters.timeJob("memory-review", this::reviewNow));
  }

  public long reviewNow() {
    double similarityThreshold = config.similarityThreshold();
    double maxCosineDistance = 1.0 - similarityThreshold;
    JobLog.Run run = JobLog.start("memory-review");
    try {
      long removed = factStore.purgeNearDuplicates(maxCosineDistance);
      run.count("removed", removed).count("threshold", similarityThreshold);
      run.finish();
      return removed;
    } catch (RuntimeException e) {
      run.fail(e);
      throw e;
    }
  }
}
