package dev.omatheusmesmo.qlawkus.store.pg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.store.ScheduledTaskStore;
import dev.omatheusmesmo.qlawkus.task.DynamicTaskScheduler;
import dev.omatheusmesmo.qlawkus.task.ScheduledTask;
import dev.omatheusmesmo.qlawkus.task.TaskDestination;
import dev.omatheusmesmo.qlawkus.task.TaskStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PgScheduledTaskStoreTest {

  private static final String RECURRING = "0 0 9 * * ? 2099";

  @Inject
  ScheduledTaskStore store;

  @Inject
  ScheduledTaskRepository repository;

  @BeforeEach
  @AfterEach
  void emptyTable() {
    repository.deleteAll();
  }

  @Test
  void aSavedTaskIsLoadedWithEveryField() {
    ScheduledTask task = task(RECURRING, TaskStatus.ACTIVE);

    store.save(task);

    assertEquals(List.of(task), store.loadAll());
  }

  @Test
  void savingAgainUpdatesTheStatusOfTheSameRecord() {
    ScheduledTask active = task(RECURRING, TaskStatus.ACTIVE);
    store.save(active);

    store.save(withStatus(active, TaskStatus.MISSED));

    List<ScheduledTask> loaded = store.loadAll();
    assertEquals(1, loaded.size());
    assertEquals(TaskStatus.MISSED, loaded.get(0).status());
  }

  @Test
  void deleteRemovesTheRecord() {
    ScheduledTask task = task(RECURRING, TaskStatus.ACTIVE);
    store.save(task);

    store.delete(task.id());

    assertTrue(store.loadAll().isEmpty());
  }

  @Test
  void theWriteProbeAcceptsTheTableTheStoreWrites() {
    store.verifyWritable();
  }

  private static ScheduledTask task(String cron, TaskStatus status) {
    return new ScheduledTask(DynamicTaskScheduler.ID_PREFIX + UUID.randomUUID(), "telegram:42",
        new TaskDestination("telegram", "42"), "stand up", cron, DynamicTaskScheduler.isOneShot(cron),
        Instant.parse("2026-11-01T10:00:00Z"), status);
  }

  private static ScheduledTask withStatus(ScheduledTask task, TaskStatus status) {
    return new ScheduledTask(task.id(), task.memoryId(), task.destination(), task.task(), task.cron(),
        task.oneShot(), task.createdAt(), status);
  }
}
