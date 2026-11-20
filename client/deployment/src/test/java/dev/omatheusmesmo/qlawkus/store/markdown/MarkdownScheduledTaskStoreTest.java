package dev.omatheusmesmo.qlawkus.store.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.task.DynamicTaskScheduler;
import dev.omatheusmesmo.qlawkus.task.ScheduledTask;
import dev.omatheusmesmo.qlawkus.task.TaskDestination;
import dev.omatheusmesmo.qlawkus.task.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The file store is the record of an agent-created task across a restart, so these pin what a
 * restarted instance can rely on: a record round-trips whole, a damaged file costs only itself, and
 * nothing outside the directory can be written or deleted through an id.
 */
class MarkdownScheduledTaskStoreTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T10:00:00Z");
    private static final String RECURRING = "0 0 9 * * ? 2099";

    @Test
    void aSavedTaskIsLoadedWithEveryField(@TempDir Path dir) {
        ScheduledTask task = task(RECURRING, TaskStatus.ACTIVE);

        new MarkdownScheduledTaskStore(dir).save(task);

        assertEquals(List.of(task), new MarkdownScheduledTaskStore(dir).loadAll());
    }

    @Test
    void anAbsentDirectoryLoadsAsEmpty(@TempDir Path root) {
        assertTrue(new MarkdownScheduledTaskStore(root.resolve("scheduled-tasks")).loadAll().isEmpty());
    }

    @Test
    void savingAgainReplacesTheRecordWithTheSameId(@TempDir Path dir) {
        MarkdownScheduledTaskStore store = new MarkdownScheduledTaskStore(dir);
        ScheduledTask active = task(RECURRING, TaskStatus.ACTIVE);
        store.save(active);

        store.save(withStatus(active, TaskStatus.INVALID));

        List<ScheduledTask> loaded = store.loadAll();
        assertEquals(1, loaded.size());
        assertEquals(TaskStatus.INVALID, loaded.get(0).status());
    }

    @Test
    void deleteRemovesTheRecordAndDoingItTwiceIsHarmless(@TempDir Path dir) {
        MarkdownScheduledTaskStore store = new MarkdownScheduledTaskStore(dir);
        ScheduledTask task = task(RECURRING, TaskStatus.ACTIVE);
        store.save(task);

        store.delete(task.id());
        store.delete(task.id());

        assertTrue(store.loadAll().isEmpty());
    }

    @Test
    void aRecordThatCannotBeParsedIsSkippedAndTheOthersStillLoad(@TempDir Path dir) throws IOException {
        MarkdownScheduledTaskStore store = new MarkdownScheduledTaskStore(dir);
        ScheduledTask good = task(RECURRING, TaskStatus.ACTIVE);
        store.save(good);
        Files.writeString(dir.resolve("qlawkus-task-" + UUID.randomUUID() + ".json"), "{ not json");

        assertEquals(List.of(good), store.loadAll());
    }

    @Test
    void aRecordWhoseIdDoesNotMatchItsFileNameIsSkipped(@TempDir Path dir) throws IOException {
        MarkdownScheduledTaskStore store = new MarkdownScheduledTaskStore(dir);
        ScheduledTask first = task(RECURRING, TaskStatus.ACTIVE);
        ScheduledTask second = task(RECURRING, TaskStatus.ACTIVE);
        store.save(first);
        store.save(second);
        Files.copy(dir.resolve(second.id() + ".json"), dir.resolve("qlawkus-task-" + UUID.randomUUID() + ".json"));

        Set<String> ids = store.loadAll().stream().map(ScheduledTask::id).collect(Collectors.toSet());

        assertEquals(Set.of(first.id(), second.id()), ids);
    }

    @Test
    void loadingIsBoundedWhateverIsOnDisk(@TempDir Path dir) {
        MarkdownScheduledTaskStore store = new MarkdownScheduledTaskStore(dir);
        for (int i = 0; i < MarkdownScheduledTaskStore.MAX_RECORDS + 5; i++) {
            store.save(task(RECURRING, TaskStatus.ACTIVE));
        }

        assertEquals(MarkdownScheduledTaskStore.MAX_RECORDS, store.loadAll().size());
    }

    @Test
    void anIdThatWouldLeaveTheDirectoryIsRefused(@TempDir Path root) {
        Path dir = root.resolve("scheduled-tasks");
        MarkdownScheduledTaskStore store = new MarkdownScheduledTaskStore(dir);
        ScheduledTask escaping = withId(task(RECURRING, TaskStatus.ACTIVE), "../escape");

        assertThrows(IllegalArgumentException.class, () -> store.save(escaping));
        assertThrows(IllegalArgumentException.class, () -> store.delete("../escape"));
        assertFalse(Files.exists(root.resolve("escape.json")));
    }

    @Test
    void aSaveLeavesOnlyTheRecordBehind(@TempDir Path dir) throws IOException {
        ScheduledTask task = task(RECURRING, TaskStatus.ACTIVE);

        new MarkdownScheduledTaskStore(dir).save(task);

        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of(task.id() + ".json"), files.map(file -> file.getFileName().toString()).toList());
        }
    }

    @Test
    void verifyWritableCreatesTheDirectoryAndLeavesNoProbeBehind(@TempDir Path root) throws IOException {
        Path dir = root.resolve("scheduled-tasks");

        new MarkdownScheduledTaskStore(dir).verifyWritable();

        assertTrue(Files.isDirectory(dir));
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void verifyWritableFailsWhenTheDirectoryCannotExist(@TempDir Path root) throws IOException {
        Path blocker = root.resolve("scheduled-tasks");
        Files.writeString(blocker, "a file where the directory should be");

        assertThrows(RuntimeException.class, new MarkdownScheduledTaskStore(blocker)::verifyWritable);
    }

    private static ScheduledTask task(String cron, TaskStatus status) {
        return new ScheduledTask("qlawkus-task-" + UUID.randomUUID(), "telegram:42",
                new TaskDestination("telegram", "42"), "stand up", cron, DynamicTaskScheduler.isOneShot(cron),
                CREATED, status);
    }

    private static ScheduledTask withStatus(ScheduledTask task, TaskStatus status) {
        return new ScheduledTask(task.id(), task.memoryId(), task.destination(), task.task(), task.cron(),
                task.oneShot(), task.createdAt(), status);
    }

    private static ScheduledTask withId(ScheduledTask task, String id) {
        return new ScheduledTask(id, task.memoryId(), task.destination(), task.task(), task.cron(),
                task.oneShot(), task.createdAt(), task.status());
    }
}
