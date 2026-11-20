package dev.omatheusmesmo.qlawkus.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownScheduledTaskStore;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

/**
 * Boots the agent over a state root that already holds records from an earlier run, which is the
 * redeploy case the restore exists for. The restorer is an eager startup bean, so these assertions
 * observe what boot itself did, not a call made by the test.
 *
 * <p>The profile seeds the directory once, before the application starts, and the ids are literal
 * constants: a profile and its test can be loaded by different classloaders, so generated values would
 * differ between the seeded records and the test's view of them.
 */
@QuarkusTest
@TestProfile(RestoredTasksAtBootTest.SeededState.class)
class RestoredTasksAtBootTest {

    @Inject
    DynamicTaskScheduler tasks;

    @Inject
    Scheduler scheduler;

    @Test
    void aStoredRecurringTaskIsRegisteredAgainAtBoot() {
        TaskView view = viewOf(SeededState.LIVE);

        assertEquals(TaskStatus.ACTIVE, view.task().status());
        assertNotNull(view.nextFire());
        assertNotNull(scheduler.getScheduledJob(SeededState.LIVE));
    }

    @Test
    void aOneShotDueWhileDownIsMissedAndNotRunAtBoot() {
        TaskView view = viewOf(SeededState.MISSED);

        assertEquals(TaskStatus.MISSED, view.task().status());
        assertNull(view.nextFire());
        assertNull(scheduler.getScheduledJob(SeededState.MISSED));
        assertEquals(TaskStatus.MISSED, recordStatus(SeededState.MISSED), "the missed state is written back");
    }

    @Test
    void anUnschedulableCronIsInvalidAtBoot() {
        assertEquals(TaskStatus.INVALID, viewOf(SeededState.INVALID).task().status());
        assertNull(scheduler.getScheduledJob(SeededState.INVALID));
    }

    @Test
    void aDamagedRecordIsLeftOnDiskAndBootCarriesOn() {
        assertTrue(Files.exists(seededDirectory().resolve(SeededState.CORRUPT)));
        assertEquals(3, tasks.list().size(), "the damaged record does not stop the other three from loading");
    }

    private TaskView viewOf(String id) {
        return tasks.list().stream()
                .filter(view -> view.task().id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("boot did not restore " + id));
    }

    private static TaskStatus recordStatus(String id) {
        return new MarkdownScheduledTaskStore(seededDirectory()).loadAll().stream()
                .filter(record -> record.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no stored record for " + id))
                .status();
    }

    private static Path seededDirectory() {
        String root = ConfigProvider.getConfig().getValue("qlawkus.agent.state.root", String.class);
        return MarkdownScheduledTaskStore.directoryUnder(root);
    }

    public static class SeededState implements QuarkusTestProfile {

        static final String LIVE = "qlawkus-task-00000000-0000-4000-8000-000000000001";
        static final String MISSED = "qlawkus-task-00000000-0000-4000-8000-000000000002";
        static final String INVALID = "qlawkus-task-00000000-0000-4000-8000-000000000003";
        static final String CORRUPT = "qlawkus-task-00000000-0000-4000-8000-0000000000ff.json";

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("qlawkus.agent.state.root", seed().toString());
        }

        private static Path seed() {
            try {
                Path state = Files.createTempDirectory("qlawkus-restore-test");
                Path dir = MarkdownScheduledTaskStore.directoryUnder(state.toString());
                MarkdownScheduledTaskStore store = new MarkdownScheduledTaskStore(dir);
                store.save(record(LIVE, "stretch", "0 0 9 * * ? 2099", TaskStatus.ACTIVE));
                store.save(record(MISSED, "pay the bill", "0 0 12 15 6 ? 2020", TaskStatus.ACTIVE));
                store.save(record(INVALID, "lunch", "every tuesday at lunch", TaskStatus.ACTIVE));
                Files.writeString(dir.resolve(CORRUPT), "{ damaged");
                return state;
            } catch (IOException e) {
                throw new IllegalStateException("cannot seed the restore test state", e);
            }
        }

        private static ScheduledTask record(String id, String task, String cron, TaskStatus status) {
            return new ScheduledTask(id, "telegram:42", new TaskDestination("telegram", "42"), task, cron,
                    DynamicTaskScheduler.isOneShot(cron), Instant.parse("2026-11-01T10:00:00Z"), status);
        }
    }
}
