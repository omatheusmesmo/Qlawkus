package dev.omatheusmesmo.qlawkus.store.pg.reconcile;

import dev.omatheusmesmo.qlawkus.store.pg.reconcile.CognitionReconciler.Direction;
import dev.omatheusmesmo.qlawkus.store.pg.reconcile.CognitionReconciler.Stats;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * One-shot, one-directional migration between the markdown files and pgvector, the explicit
 * counterpart to {@link CognitionReconciler}'s non-destructive union. Use it to move a deployment
 * between backends: build with the pgvector extension present (pgvector or hybrid), point the file
 * roots at the source/target {@code .md} tree, and run a direction. Unlike reconcile, a migrate
 * <strong>overwrites</strong> the singletons (persona, owner profile) on the destination.
 */
@ApplicationScoped
public class CognitionMigrator {

  @Inject
  CognitionReconciler reconciler;

  /** Copies every store one way. {@code FILES_TO_PG} or {@code PG_TO_FILES} only. */
  public Stats migrate(Direction direction) {
    if (direction == Direction.BOTH) {
      throw new IllegalArgumentException(
          "migrate requires a concrete direction (FILES_TO_PG or PG_TO_FILES); "
              + "use CognitionReconciler.reconcileAll() for the union");
    }
    Log.warnf("Cognition migrate %s starting (overwrites destination singletons)", direction);
    Stats stats = reconciler.reconcile(direction);
    Log.infof("Cognition migrate %s complete: +%d to pg, +%d to files",
        direction, stats.toPg(), stats.toFiles());
    return stats;
  }
}
