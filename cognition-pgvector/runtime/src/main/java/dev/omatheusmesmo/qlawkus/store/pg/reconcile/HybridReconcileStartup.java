package dev.omatheusmesmo.qlawkus.store.pg.reconcile;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * In {@code hybrid} mode, reconciles files and pgvector once at boot so a deployment that just
 * switched into hybrid (from a markdown-only or pgvector-only build) backfills the missing mirror.
 * Only active in hybrid builds; gate it off with {@code qlawkus.cognition.reconcile-at-start=false}
 * (e.g. for a very large dataset, then trigger reconciliation manually via the admin endpoint).
 * Failures are logged, never fatal to boot.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.cognition.backend", stringValue = "hybrid")
public class HybridReconcileStartup {

  @Inject
  CognitionReconciler reconciler;

  @ConfigProperty(name = "qlawkus.cognition.reconcile-at-start", defaultValue = "true")
  boolean reconcileAtStart;

  void onStart(@Observes StartupEvent event) {
    if (!reconcileAtStart) {
      Log.debug("Hybrid reconcile-at-start disabled; skipping");
      return;
    }
    try {
      reconciler.reconcileAll();
    } catch (RuntimeException e) {
      Log.warnf(e, "Hybrid startup reconciliation failed; continuing boot. "
          + "Run POST /api/admin/cognition/reconcile to retry.");
    }
  }
}
