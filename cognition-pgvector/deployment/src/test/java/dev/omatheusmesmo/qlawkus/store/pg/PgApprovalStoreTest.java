package dev.omatheusmesmo.qlawkus.store.pg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.approval.ApprovalRequest;
import dev.omatheusmesmo.qlawkus.approval.ApprovalStatus;
import dev.omatheusmesmo.qlawkus.approval.ApprovalStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PgApprovalStoreTest {

  private static final Instant CREATED = Instant.parse("2026-11-20T10:00:00Z");

  @Inject
  ApprovalStore store;

  @Inject
  ApprovalRequestRepository repository;

  @BeforeEach
  @AfterEach
  void emptyTable() {
    repository.deleteAll();
  }

  @Test
  void aCreatedRequestIsFoundAgainWithEveryField() {
    ApprovalRequest request = request(newId(), ApprovalStatus.PENDING, CREATED);

    store.create(request);

    assertEquals(Optional.of(request), store.find(request.id()));
    assertEquals(List.of(request), store.list());
  }

  @Test
  void creatingTheSameIdTwiceIsAnErrorAndNeverAnOverwrite() {
    ApprovalRequest request = request(newId(), ApprovalStatus.PENDING, CREATED);
    store.create(request);

    assertThrows(IllegalStateException.class,
        () -> store.create(request(request.id(), ApprovalStatus.APPROVED, CREATED)));

    assertEquals(ApprovalStatus.PENDING, store.find(request.id()).orElseThrow().status());
  }

  @Test
  void aTransitionAppliesOnlyWhileTheRecordStillHasTheExpectedStatus() {
    ApprovalRequest request = request(newId(), ApprovalStatus.PENDING, CREATED);
    store.create(request);

    Optional<ApprovalRequest> won = store.transition(request.id(), ApprovalStatus.PENDING, approve());
    Optional<ApprovalRequest> lost = store.transition(request.id(), ApprovalStatus.PENDING, approve());

    assertEquals(ApprovalStatus.APPROVED, won.orElseThrow().status());
    assertTrue(lost.isEmpty());
    assertEquals("owner", store.find(request.id()).orElseThrow().decidedBy());
  }

  @Test
  void ofManyConcurrentApprovalsExactlyOneWins() throws Exception {
    ApprovalRequest request = request(newId(), ApprovalStatus.PENDING, CREATED);
    store.create(request);
    int callers = 8;
    ExecutorService pool = Executors.newFixedThreadPool(callers);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < callers; i++) {
        Callable<Boolean> call = () -> {
          start.await();
          return store.transition(request.id(), ApprovalStatus.PENDING, approve()).isPresent();
        };
        results.add(pool.submit(call));
      }
      start.countDown();
      long winners = 0;
      for (Future<Boolean> result : results) {
        if (result.get()) {
          winners++;
        }
      }
      assertEquals(1, winners);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void aTransitionOfAnUnknownIdChangesNothing() {
    assertTrue(store.transition(newId(), ApprovalStatus.PENDING, approve()).isEmpty());
    assertTrue(store.list().isEmpty());
  }

  @Test
  void listIsNewestFirst() {
    ApprovalRequest older = request(newId(), ApprovalStatus.PENDING, CREATED);
    ApprovalRequest newer = request(newId(), ApprovalStatus.PENDING, CREATED.plusSeconds(60));
    store.create(older);
    store.create(newer);

    assertEquals(List.of(newer.id(), older.id()), store.list().stream().map(ApprovalRequest::id).toList());
  }

  @Test
  void theOldestTerminalRequestsArePrunedAndPendingOnesAreKept() {
    ApprovalRequest pending = request(newId(), ApprovalStatus.PENDING, CREATED);
    ApprovalRequest oldDone = request(newId(), ApprovalStatus.EXECUTED, CREATED.plusSeconds(1));
    ApprovalRequest newDone = request(newId(), ApprovalStatus.DENIED, CREATED.plusSeconds(2));
    List.of(pending, oldDone, newDone).forEach(store::create);

    repository.pruneTerminal(1);

    List<String> kept = store.list().stream().map(ApprovalRequest::id).toList();
    assertTrue(kept.contains(pending.id()));
    assertTrue(kept.contains(newDone.id()));
    assertEquals(2, kept.size());
  }

  @Test
  void anUnknownOrUnsafeIdIsNeverLookedUp() {
    assertTrue(store.find("../etc/passwd").isEmpty());
    assertTrue(store.find(null).isEmpty());
  }

  @Test
  void theWriteProbeAcceptsTheTableTheStoreWrites() {
    store.verifyWritable();
  }

  private static UnaryOperator<ApprovalRequest> approve() {
    return pending -> pending.decide(ApprovalStatus.APPROVED, CREATED.plusSeconds(300), "owner");
  }

  private static String newId() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  private static ApprovalRequest request(String id, ApprovalStatus status, Instant createdAt) {
    return new ApprovalRequest(id, "sendEmail", "{\"to\":\"ana@example.com\"}", "hash", "sendEmail {...}",
        "telegram:42", "telegram", "42", status, createdAt, createdAt.plusSeconds(86_400), null, null, null);
  }
}
