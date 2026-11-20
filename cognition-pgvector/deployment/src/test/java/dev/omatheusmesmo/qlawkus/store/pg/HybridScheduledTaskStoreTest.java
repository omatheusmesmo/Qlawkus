package dev.omatheusmesmo.qlawkus.store.pg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownScheduledTaskStore;
import dev.omatheusmesmo.qlawkus.task.DynamicTaskScheduler;
import dev.omatheusmesmo.qlawkus.task.ScheduledTask;
import dev.omatheusmesmo.qlawkus.task.TaskDestination;
import dev.omatheusmesmo.qlawkus.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The hybrid store keeps the files as the source of truth and the table as a mirror, so a read must
 * come from the files and a write must reach both. The table is an in-memory fake, as in the other
 * hybrid store tests, so the file and mirror logic runs without Postgres.
 */
class HybridScheduledTaskStoreTest {

  private static final String RECURRING = "0 0 9 * * ? 2099";

  @TempDir
  Path tempDir;

  @Test
  void aSaveReachesTheFilesAndTheMirror() {
    FakeRepository mirror = new FakeRepository();
    ScheduledTask task = task();

    store(mirror).save(task);

    assertTrue(Files.exists(directory().resolve(task.id() + ".json")));
    assertEquals(List.of(task), mirror.listAll());
  }

  @Test
  void aLoadReadsTheFilesNotTheMirror() {
    FakeRepository mirror = new FakeRepository();
    ScheduledTask task = task();
    new MarkdownScheduledTaskStore(directory()).save(task);

    assertEquals(List.of(task), store(mirror).loadAll());
    assertTrue(mirror.listAll().isEmpty(), "nothing was mirrored, so the table is not the source");
  }

  @Test
  void aDeleteReachesTheFilesAndTheMirror() {
    FakeRepository mirror = new FakeRepository();
    HybridScheduledTaskStore store = store(mirror);
    ScheduledTask task = task();
    store.save(task);

    store.delete(task.id());

    assertFalse(Files.exists(directory().resolve(task.id() + ".json")));
    assertTrue(mirror.listAll().isEmpty());
  }

  @Test
  void verifyWritableProbesTheFilesAndTheMirror() {
    RecordingWriteProbe probe = new RecordingWriteProbe();

    new HybridScheduledTaskStore(directory(), new FakeRepository(), probe).verifyWritable();

    assertEquals(List.of(PgWriteProbe.Table.SCHEDULED_TASK), probe.probed);
  }

  private HybridScheduledTaskStore store(FakeRepository mirror) {
    return new HybridScheduledTaskStore(directory(), mirror, new RecordingWriteProbe());
  }

  private Path directory() {
    return tempDir.resolve(MarkdownScheduledTaskStore.DIRECTORY);
  }

  private static ScheduledTask task() {
    return new ScheduledTask(DynamicTaskScheduler.ID_PREFIX + UUID.randomUUID(), "telegram:42",
        new TaskDestination("telegram", "42"), "stand up", RECURRING, false,
        Instant.parse("2026-11-01T10:00:00Z"), TaskStatus.ACTIVE);
  }

  private static final class FakeRepository extends ScheduledTaskRepository {

    private final Map<String, ScheduledTask> rows = new LinkedHashMap<>();

    @Override
    public void save(ScheduledTask scheduled) {
      rows.put(scheduled.id(), scheduled);
    }

    @Override
    public void delete(String id) {
      rows.remove(id);
    }

    @Override
    public List<ScheduledTask> listAll() {
      return List.copyOf(rows.values());
    }

    @Override
    public void deleteAll() {
      rows.clear();
    }
  }
}
