package dev.omatheusmesmo.qlawkus.store.pg.reconcile;

import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import dev.omatheusmesmo.qlawkus.store.pg.CognitionBackendState;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * In {@code hybrid} mode, reconciles files and pgvector once at boot so a deployment that just
 * switched into hybrid (from a markdown-only or pgvector-only build) backfills the missing mirror.
 * Only active in hybrid builds; gate it off with {@code qlawkus.cognition.reconcile-at-start=false}
 * (e.g. for a very large dataset, then trigger reconciliation manually via the admin endpoint).
 * Failures are logged, never fatal to boot.
 *
 * <p>It runs on its own thread after boot, on the {@code batch} workload, and never inside startup:
 * mirroring a file-only fact embeds it through the provider, so an unreachable or throttled provider
 * would otherwise keep the app from listening for as long as the retries last (minutes, measured).
 * Readiness does not wait for it, and a run cut short resumes at the next boot since it is idempotent.
 */
@ApplicationScoped
@Typed(HybridReconcileStartup.class)
public class HybridReconcileStartup {

  static final String JOB = "hybrid-reconcile";

  @Inject
  CognitionReconciler reconciler;

  @Inject
  CognitionBackendState backend;

  @Inject
  AgentMeters meters;

  @ConfigProperty(name = "qlawkus.cognition.reconcile-at-start", defaultValue = "true")
  boolean reconcileAtStart;

  void onStart(@Observes StartupEvent event) {
    if (!backend.isHybrid()) {
      return;
    }
    if (!reconcileAtStart) {
      Log.debug("Hybrid reconcile-at-start disabled; skipping");
      return;
    }
    Thread.ofVirtual().name(JOB).start(() -> {
      try {
        WorkloadContext.runAs(WorkloadContext.BATCH, () -> meters.timeJob(JOB, reconciler::reconcileAll));
      } catch (RuntimeException e) {
        Log.warnf(e, "Hybrid startup reconciliation failed; boot is unaffected. "
            + "Run POST /api/admin/cognition/reconcile to retry.");
      }
    });
  }
}
