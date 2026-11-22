package dev.omatheusmesmo.qlawkus.task;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.AgentRunner;
import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.ScheduledTaskStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownScheduledTaskStore;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins what an agent-created task leaves in the store and what a restarted scheduler takes back out
 * of it. A restart is simulated as the real one would be: the Quartz job is gone with the process,
 * and only the record survives, so a second {@link DynamicTaskScheduler} over the same directory has
 * to rebuild the registration from the record alone.
 */
@QuarkusTest
class DynamicTaskSchedulerPersistenceTest {

    private static final String MEMORY = "telegram:42";
    private static final TaskDestination TELEGRAM = new TaskDestination("telegram", "42");
    private static final String RECURRING = "0 0 9 * * ? 2099";
    private static final String ONE_SHOT = "0 0 12 15 6 ? 2099";
    private static final String PAST_ONE_SHOT = "0 0 12 15 6 ? 2020";

    @Inject
    DynamicTaskScheduler tasks;

    @Inject
    Scheduler scheduler;

    @Inject
    AgentConfig config;

    @Inject
    AgentDeliveryContext delivery;

    @Inject
    ScheduledTaskStore store;

    @Inject
    Event<WorkflowResultReadyEvent> events;

    @InjectMock
    AgentRunner runner;

    @BeforeEach
    void startFromNoTasks() {
        delivery.clear();
        tasks.list().forEach(view -> tasks.cancel(view.task().id()));
    }

    @AfterEach
    void cancelEverythingItCreated() {
        delivery.clear();
        tasks.list().forEach(view -> tasks.cancel(view.task().id()));
    }

    @Test
    void createWritesTheRecordBeforeItReturns() {
        String id = tasks.create(MEMORY, TELEGRAM, "stretch", RECURRING).task().id();

        ScheduledTask record = recordOf(id);
        assertEquals("stretch", record.task());
        assertEquals(TaskStatus.ACTIVE, record.status());
        assertEquals(TELEGRAM, record.destination());
    }

    @Test
    void cancelRemovesTheRecord() {
        String id = tasks.create(MEMORY, TELEGRAM, "stretch", RECURRING).task().id();

        assertTrue(tasks.cancel(id));

        assertFalse(hasRecord(id));
    }

    @Test
    void aRecurringTaskKeepsItsRecordAfterEachRun() {
        String id = tasks.create(MEMORY, TELEGRAM, "stretch", RECURRING).task().id();
        when(runner.chat(eq(MEMORY), anyString())).thenReturn("time to stretch");

        tasks.fire(id);

        assertEquals(TaskStatus.ACTIVE, recordOf(id).status());
    }

    @Test
    void aOneShotRecordIsRemovedBeforeTheModelRuns() {
        String id = tasks.create(MEMORY, TELEGRAM, "pay the bill", ONE_SHOT).task().id();
        AtomicReference<Boolean> recordedDuringRun = new AtomicReference<>();
        when(runner.chat(eq(MEMORY), anyString())).thenAnswer(invocation -> {
            recordedDuringRun.set(hasRecord(id));
            return "paid";
        });

        tasks.fire(id);

        assertFalse(recordedDuringRun.get(), "the record is gone when the model is called");
        assertFalse(hasRecord(id));
    }

    @Test
    void aOneShotRecordIsRemovedEvenWhenItsRunFails() {
        String id = tasks.create(MEMORY, TELEGRAM, "pay the bill", ONE_SHOT).task().id();
        when(runner.chat(eq(MEMORY), anyString())).thenThrow(new IllegalStateException("model unavailable"));

        assertDoesNotThrow(() -> tasks.fire(id));

        assertFalse(hasRecord(id));
    }

    @Test
    void aRestartedSchedulerRegistersEveryStoredTaskAgain(@TempDir Path dir) {
        String id = schedulerOver(new MarkdownScheduledTaskStore(dir))
                .create(MEMORY, TELEGRAM, "stretch", RECURRING).task().id();
        scheduler.unscheduleJob(id);
        DynamicTaskScheduler restarted = schedulerOver(new MarkdownScheduledTaskStore(dir));
        try {
            TaskRestoreReport report = restarted.restore();

            assertEquals(1, report.restored());
            assertNotNull(scheduler.getScheduledJob(id), "the job is registered again under the same id");
            TaskView view = restarted.list().get(0);
            assertEquals(id, view.task().id());
            assertEquals(TaskStatus.ACTIVE, view.task().status());
            assertNotNull(view.nextFire());
        } finally {
            restarted.cancel(id);
        }
    }

    @Test
    void restoringTwiceDoesNotRegisterATaskTwice(@TempDir Path dir) {
        String id = schedulerOver(new MarkdownScheduledTaskStore(dir))
                .create(MEMORY, TELEGRAM, "stretch", RECURRING).task().id();
        scheduler.unscheduleJob(id);
        DynamicTaskScheduler restarted = schedulerOver(new MarkdownScheduledTaskStore(dir));
        try {
            restarted.restore();

            TaskRestoreReport again = restarted.restore();

            assertEquals(0, again.restored());
            assertEquals(1, restarted.list().size());
            assertNotNull(scheduler.getScheduledJob(id));
        } finally {
            restarted.cancel(id);
        }
    }

    @Test
    void restoreStopsAtTheTaskCap(@TempDir Path dir) {
        MarkdownScheduledTaskStore disk = new MarkdownScheduledTaskStore(dir);
        for (int i = 0; i < DynamicTaskScheduler.MAX_TASKS + 3; i++) {
            disk.save(recordWith(RECURRING, TaskStatus.ACTIVE));
        }
        DynamicTaskScheduler restarted = schedulerOver(new MarkdownScheduledTaskStore(dir));
        try {
            TaskRestoreReport report = restarted.restore();

            assertEquals(DynamicTaskScheduler.MAX_TASKS, report.restored());
            assertEquals(3, report.held(), "the tasks over the cap stay stored and are not registered");
            assertEquals(DynamicTaskScheduler.MAX_TASKS, restarted.list().size());
        } finally {
            restarted.list().forEach(view -> restarted.cancel(view.task().id()));
        }
    }

    @Test
    void aOneShotDueWhileTheAgentWasDownIsMarkedMissedAndNeverRun(@TempDir Path dir) {
        MarkdownScheduledTaskStore disk = new MarkdownScheduledTaskStore(dir);
        ScheduledTask due = recordWith(PAST_ONE_SHOT, TaskStatus.ACTIVE);
        disk.save(due);
        DynamicTaskScheduler restarted = schedulerOver(new MarkdownScheduledTaskStore(dir));
        try {
            TaskRestoreReport report = restarted.restore();

            assertEquals(1, report.missed());
            assertEquals(0, report.restored());
            TaskView view = restarted.list().get(0);
            assertEquals(TaskStatus.MISSED, view.task().status());
            assertNull(view.nextFire());
            assertNull(scheduler.getScheduledJob(due.id()));
            assertEquals(TaskStatus.MISSED, recordOf(due.id(), dir).status(), "the missed state is on disk");

            restarted.fire(due.id());

            verifyNoInteractions(runner);
        } finally {
            assertTrue(restarted.cancel(due.id()), "a missed task can be cancelled, which removes it");
            assertFalse(hasRecord(due.id(), dir));
        }
    }

    @Test
    void aOneShotInTheFutureIsRegisteredAgain(@TempDir Path dir) {
        ScheduledTask future = recordWith(ONE_SHOT, TaskStatus.ACTIVE);
        new MarkdownScheduledTaskStore(dir).save(future);
        DynamicTaskScheduler restarted = schedulerOver(new MarkdownScheduledTaskStore(dir));
        try {
            TaskRestoreReport report = restarted.restore();

            assertEquals(1, report.restored());
            assertNotNull(scheduler.getScheduledJob(future.id()));
            Instant nextFire = restarted.list().get(0).nextFire();
            assertEquals(2099, nextFire.atZone(ZoneId.of(config.timezone())).getYear());
        } finally {
            restarted.cancel(future.id());
        }
    }

    @Test
    void aCronThatCannotBeScheduledIsMarkedInvalidAndNotRetried(@TempDir Path dir) {
        ScheduledTask broken = recordWith("every tuesday at lunch", TaskStatus.ACTIVE);
        new MarkdownScheduledTaskStore(dir).save(broken);
        DynamicTaskScheduler restarted = schedulerOver(new MarkdownScheduledTaskStore(dir));
        try {
            TaskRestoreReport report = restarted.restore();

            assertEquals(1, report.invalid());
            assertEquals(TaskStatus.INVALID, restarted.list().get(0).task().status());
            assertNull(scheduler.getScheduledJob(broken.id()));
            assertEquals(TaskStatus.INVALID, recordOf(broken.id(), dir).status());

            TaskRestoreReport nextStart = schedulerOver(new MarkdownScheduledTaskStore(dir)).restore();

            assertEquals(0, nextStart.restored());
            assertEquals(0, nextStart.invalid(), "an invalid record is reported once, not re-evaluated forever");
            assertEquals(TaskStatus.INVALID, recordOf(broken.id(), dir).status());
        } finally {
            restarted.cancel(broken.id());
        }
    }

    @Test
    void aCronThatNeverFiresAgainIsMarkedInvalid(@TempDir Path dir) {
        ScheduledTask never = recordWith("0 0 12 31 2 ? *", TaskStatus.ACTIVE);
        new MarkdownScheduledTaskStore(dir).save(never);
        DynamicTaskScheduler restarted = schedulerOver(new MarkdownScheduledTaskStore(dir));
        try {
            TaskRestoreReport report = restarted.restore();

            assertEquals(1, report.invalid());
            assertNull(scheduler.getScheduledJob(never.id()));
        } finally {
            restarted.cancel(never.id());
        }
    }

    private DynamicTaskScheduler schedulerOver(ScheduledTaskStore backing) {
        return new DynamicTaskScheduler(scheduler, runner, delivery, events, mock(Event.class),
                config, backing, mock(TaskAudit.class));
    }

    private ScheduledTask recordOf(String id) {
        return recordOf(id, null);
    }

    private ScheduledTask recordOf(String id, Path dir) {
        ScheduledTaskStore source = dir == null ? store : new MarkdownScheduledTaskStore(dir);
        return source.loadAll().stream()
                .filter(record -> record.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no stored record for " + id));
    }

    private boolean hasRecord(String id) {
        return hasRecord(id, null);
    }

    private boolean hasRecord(String id, Path dir) {
        ScheduledTaskStore source = dir == null ? store : new MarkdownScheduledTaskStore(dir);
        return source.loadAll().stream().anyMatch(record -> record.id().equals(id));
    }

    private static ScheduledTask recordWith(String cron, TaskStatus status) {
        return new ScheduledTask(DynamicTaskScheduler.ID_PREFIX + UUID.randomUUID(), MEMORY, TELEGRAM, "stretch",
                cron, DynamicTaskScheduler.isOneShot(cron), ZonedDateTime.now(ZoneId.of("UTC")).toInstant(), status);
    }
}
