package dev.omatheusmesmo.qlawkus.store.pg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.approval.ApprovalRequest;
import dev.omatheusmesmo.qlawkus.approval.ApprovalStatus;
import dev.omatheusmesmo.qlawkus.approval.FileApprovalStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The hybrid store keeps the files as the source of truth and the table as a mirror, so a read must
 * come from the files and every write must reach both. The table is an in-memory fake, as in the other
 * hybrid store tests, so the file and mirror logic runs without Postgres.
 */
class HybridApprovalStoreTest {

  private static final String ID = "0123456789abcdef0123456789abcdef";

  @TempDir
  Path tempDir;

  @Test
  void aCreateReachesTheFilesAndTheMirror() {
    FakeRepository mirror = new FakeRepository();
    ApprovalRequest request = request(ID, ApprovalStatus.PENDING);

    store(mirror).create(request);

    assertTrue(Files.exists(directory().resolve(ID + ".json")));
    assertEquals(Optional.of(request), mirror.find(ID));
  }

  @Test
  void aReadComesFromTheFilesNotTheMirror() {
    FakeRepository mirror = new FakeRepository();
    ApprovalRequest request = request(ID, ApprovalStatus.PENDING);
    new FileApprovalStore(directory(), FileApprovalStore.DEFAULT_MAX_TERMINAL).create(request);

    HybridApprovalStore store = store(mirror);

    assertEquals(List.of(request), store.list());
    assertEquals(Optional.of(request), store.find(ID));
    assertTrue(mirror.rows.isEmpty(), "nothing was mirrored, so the table is not the source");
  }

  @Test
  void aWonTransitionIsMirroredAndALostOneIsNot() {
    FakeRepository mirror = new FakeRepository();
    HybridApprovalStore store = store(mirror);
    store.create(request(ID, ApprovalStatus.PENDING));
    int writesAfterCreate = mirror.writes;

    Optional<ApprovalRequest> won = store.transition(ID, ApprovalStatus.PENDING, approve());
    Optional<ApprovalRequest> lost = store.transition(ID, ApprovalStatus.PENDING, approve());

    assertTrue(won.isPresent());
    assertTrue(lost.isEmpty());
    assertEquals(ApprovalStatus.APPROVED, mirror.find(ID).orElseThrow().status());
    assertEquals(writesAfterCreate + 1, mirror.writes);
  }

  @Test
  void aMirrorFailureNeverUndoesOrBlocksADecisionTheFilesRecorded() {
    FakeRepository mirror = new FakeRepository();
    HybridApprovalStore store = store(mirror);
    store.create(request(ID, ApprovalStatus.PENDING));
    mirror.failing = true;

    Optional<ApprovalRequest> won = store.transition(ID, ApprovalStatus.PENDING, approve());

    assertEquals(ApprovalStatus.APPROVED, won.orElseThrow().status());
    assertEquals(ApprovalStatus.APPROVED, store.find(ID).orElseThrow().status());
  }

  @Test
  void aRequestSurvivesARestartBecauseANewStoreReadsTheSameFiles() {
    FakeRepository mirror = new FakeRepository();
    store(mirror).create(request(ID, ApprovalStatus.PENDING));

    HybridApprovalStore restarted = store(new FakeRepository());

    assertEquals(ApprovalStatus.PENDING, restarted.find(ID).orElseThrow().status());
  }

  @Test
  void verifyWritableProbesTheFilesAndTheMirror() {
    RecordingWriteProbe probe = new RecordingWriteProbe();

    new HybridApprovalStore(directory(), FileApprovalStore.DEFAULT_MAX_TERMINAL, new FakeRepository(), probe)
        .verifyWritable();

    assertEquals(List.of(PgWriteProbe.Table.APPROVAL_REQUEST), probe.probed);
    assertFalse(Files.exists(directory().resolve(".probe")), "the file probe leaves nothing behind");
  }

  private HybridApprovalStore store(FakeRepository mirror) {
    return new HybridApprovalStore(directory(), FileApprovalStore.DEFAULT_MAX_TERMINAL, mirror,
        new RecordingWriteProbe());
  }

  private Path directory() {
    return tempDir.resolve(FileApprovalStore.DIRECTORY);
  }

  private static UnaryOperator<ApprovalRequest> approve() {
    return pending -> pending.decide(ApprovalStatus.APPROVED, Instant.parse("2026-11-20T10:05:00Z"), "owner");
  }

  static ApprovalRequest request(String id, ApprovalStatus status) {
    return new ApprovalRequest(id, "sendEmail", "{\"to\":\"ana@example.com\"}", "hash", "sendEmail {...}",
        "telegram:42", "telegram", "42", status, Instant.parse("2026-11-20T10:00:00Z"),
        Instant.parse("2026-11-21T10:00:00Z"), null, null, null);
  }

  private static final class FakeRepository extends ApprovalRequestRepository {

    final Map<String, ApprovalRequest> rows = new LinkedHashMap<>();
    int writes;
    boolean failing;

    @Override
    public void upsert(ApprovalRequest request) {
      if (failing) {
        throw new IllegalStateException("table unavailable");
      }
      writes++;
      rows.put(request.id(), request);
    }

    @Override
    public Optional<ApprovalRequest> find(String id) {
      return Optional.ofNullable(rows.get(id));
    }
  }
}
