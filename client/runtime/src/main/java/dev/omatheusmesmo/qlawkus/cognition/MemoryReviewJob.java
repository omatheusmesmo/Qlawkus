package dev.omatheusmesmo.qlawkus.cognition;

import dev.omatheusmesmo.qlawkus.store.pg.EmbeddingRepository;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Background self-review of long-term memory (inspired by Hermes' self-improvement loop). The
 * write-time MD5 dedup only catches byte-identical facts, so reworded variants of the same fact
 * accumulate over time. This job periodically removes semantic near-duplicates from the fact store,
 * keeping memory compact and reducing noise injected by active memory.
 */
@ApplicationScoped
public class MemoryReviewJob {

  @Inject
  EmbeddingRepository embeddingRepository;

  @ConfigProperty(name = "qlawkus.memory-review.similarity-threshold", defaultValue = "0.97")
  double similarityThreshold;

  @Scheduled(cron = "{qlawkus.memory-review.cron:0 30 3 * * ?}")
  void review() {
    reviewNow();
  }

  @Transactional
  public long reviewNow() {
    double maxCosineDistance = 1.0 - similarityThreshold;
    long removed = embeddingRepository.deleteNearDuplicates(maxCosineDistance);
    if (removed > 0) {
      Log.infof("MemoryReviewJob: removed %d near-duplicate memories (>= %.2f cosine similarity)",
          removed, similarityThreshold);
    }
    return removed;
  }
}
