package dev.omatheusmesmo.qlawkus.store.pg.reconcile;

import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import dev.omatheusmesmo.qlawkus.store.pg.CognitionBackendState;
import io.quarkus.runtime.StartupEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins that the boot-time reconcile never holds up startup: it embeds every file-only fact through the
 * provider, so an unreachable or throttled provider would otherwise keep the app from listening for as
 * long as the retries last.
 */
class HybridReconcileStartupTest {

  @Test
  void onStartReturnsWhileTheReconcileRunsInTheBackgroundOnTheBatchWorkload() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(1);
    AtomicReference<String> workload = new AtomicReference<>();
    CognitionReconciler reconciler = mock(CognitionReconciler.class);
    when(reconciler.reconcileAll()).thenAnswer(invocation -> {
      workload.set(WorkloadContext.current());
      started.countDown();
      release.await(10, TimeUnit.SECONDS);
      finished.countDown();
      return null;
    });
    CognitionBackendState backend = mock(CognitionBackendState.class);
    when(backend.isHybrid()).thenReturn(true);
    HybridReconcileStartup startup = new HybridReconcileStartup();
    startup.reconciler = reconciler;
    startup.backend = backend;
    startup.meters = new AgentMeters();
    startup.reconcileAtStart = true;

    startup.onStart(mock(StartupEvent.class));

    assertTrue(started.await(5, TimeUnit.SECONDS), "the reconcile should have started");
    assertEquals(1, finished.getCount(), "onStart must return before the reconcile finishes");
    assertEquals(WorkloadContext.BATCH, workload.get());
    release.countDown();
    assertTrue(finished.await(5, TimeUnit.SECONDS));
  }
}
