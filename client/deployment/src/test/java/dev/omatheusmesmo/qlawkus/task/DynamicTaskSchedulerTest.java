package dev.omatheusmesmo.qlawkus.task;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.AgentRunner;
import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.logging.CapturedLogs;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.LogRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Pins the agent-created task registry against the real Quarkus scheduler. Crons are far in the future
 * so the scheduler never fires them on its own; every run is triggered directly through
 * {@link DynamicTaskScheduler#fire(String)}.
 */
@QuarkusTest
class DynamicTaskSchedulerTest {

    private static final String MEMORY = "telegram:42";
    private static final TaskDestination TELEGRAM = new TaskDestination("telegram", "42");
    private static final String RECURRING = "0 0 9 * * ? 2099";
    private static final String ONE_SHOT = "0 0 12 15 6 ? 2099";

    @Inject
    DynamicTaskScheduler tasks;

    @Inject
    Scheduler scheduler;

    @Inject
    AgentConfig config;

    @Inject
    AgentDeliveryContext delivery;

    @InjectMock
    AgentRunner runner;

    @BeforeEach
    void clearDeliveryAndProbe() {
        delivery.clear();
        ResultProbe.RESULTS.clear();
    }

    @AfterEach
    void cancelEverythingItCreated() {
        delivery.clear();
        tasks.list().forEach(view -> tasks.cancel(view.task().id()));
    }

    @Test
    void aRecurringTaskGetsANamespacedIdAndFiresInTheConfiguredZone() {
        TaskView view = tasks.create(MEMORY, TELEGRAM, "stand up", RECURRING);

        assertTrue(view.task().id().startsWith(DynamicTaskScheduler.ID_PREFIX), view.task().id());
        assertFalse(view.task().oneShot());
        ZonedDateTime local = view.nextFire().atZone(ZoneId.of(config.timezone()));
        assertEquals(LocalTime.of(9, 0), local.toLocalTime(), "the cron is read in qlawkus.agent.timezone");
        assertNotNull(scheduler.getScheduledJob(view.task().id()), "the job is registered with the scheduler");
    }

    @Test
    void anExactDateCronIsRecognisedAsOneShot() {
        assertTrue(DynamicTaskScheduler.isOneShot(ONE_SHOT));
        assertFalse(DynamicTaskScheduler.isOneShot(RECURRING));
        assertFalse(DynamicTaskScheduler.isOneShot("0 0 9 * * ?"));
        assertFalse(DynamicTaskScheduler.isOneShot("0 0 9 15 6 ? *"), "a wildcard year is not a single date");
        assertFalse(DynamicTaskScheduler.isOneShot("0 */5 9 15 6 ? 2099"), "a step makes it fire more than once");
    }

    @Test
    void aOneShotTaskIsUnscheduledBeforeTheModelRuns() {
        String id = tasks.create(MEMORY, TELEGRAM, "pay the bill", ONE_SHOT).task().id();
        AtomicReference<Boolean> stillScheduledDuringRun = new AtomicReference<>();
        when(runner.chat(eq(MEMORY), anyString())).thenAnswer(invocation -> {
            stillScheduledDuringRun.set(scheduler.getScheduledJob(id) != null);
            return "paid";
        });

        tasks.fire(id);

        assertFalse(Boolean.TRUE.equals(stillScheduledDuringRun.get()), "the job is already gone when the model is called");
        assertNull(scheduler.getScheduledJob(id));
        assertTrue(tasks.list().stream().noneMatch(view -> view.task().id().equals(id)));
    }

    @Test
    void aOneShotTaskIsRemovedEvenWhenItsRunFails() {
        String id = tasks.create(MEMORY, TELEGRAM, "pay the bill", ONE_SHOT).task().id();
        when(runner.chat(eq(MEMORY), anyString())).thenThrow(new IllegalStateException("model unavailable"));

        assertDoesNotThrow(() -> tasks.fire(id), "a failed run is logged, never thrown into the scheduler");

        assertNull(scheduler.getScheduledJob(id));
        assertTrue(tasks.list().stream().noneMatch(view -> view.task().id().equals(id)));
    }

    @Test
    void aOneShotFiredByTheRealSchedulerRunsOnceAndRemovesItself() {
        ZonedDateTime soon = ZonedDateTime.now(ZoneId.of(config.timezone())).plusSeconds(3).truncatedTo(ChronoUnit.SECONDS);
        String cron = String.format("%d %d %d %d %d ? %d", soon.getSecond(), soon.getMinute(), soon.getHour(),
                soon.getDayOfMonth(), soon.getMonthValue(), soon.getYear());
        String id = tasks.create(MEMORY, TELEGRAM, "ring the bell", cron).task().id();
        when(runner.chat(eq(MEMORY), anyString())).thenReturn("ding");

        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> !ResultProbe.RESULTS.isEmpty());

        Mockito.verify(runner, Mockito.times(1)).chat(eq(MEMORY), anyString());
        assertEquals(List.of(new Delivered("telegram", "42", "ding")), ResultProbe.RESULTS);
        assertNull(scheduler.getScheduledJob(id), "the scheduler dropped the one-shot after its run");
    }

    @Test
    void aRecurringTaskRemainsScheduledAfterItsRun() {
        String id = tasks.create(MEMORY, TELEGRAM, "stand up", RECURRING).task().id();
        when(runner.chat(eq(MEMORY), anyString())).thenReturn("standing up");

        tasks.fire(id);

        assertNotNull(scheduler.getScheduledJob(id));
        assertTrue(tasks.list().stream().anyMatch(view -> view.task().id().equals(id)));
    }

    @Test
    void aRunUsesTheOriginConversationAndDeliversToTheDestinationOnTheInteractiveWorkload() {
        String id = tasks.create(MEMORY, TELEGRAM, "stretch", RECURRING).task().id();
        AtomicReference<Seen> seen = new AtomicReference<>();
        when(runner.chat(eq(MEMORY), anyString())).thenAnswer(invocation -> {
            seen.set(new Seen(delivery.memoryId(), delivery.providerId(), delivery.chatId(),
                    WorkloadContext.current(), invocation.getArgument(1)));
            return "time to stretch";
        });

        tasks.fire(id);

        Seen observed = seen.get();
        assertEquals(MEMORY, observed.memoryId(), "the run keeps the conversation that scheduled it");
        assertEquals("telegram", observed.providerId(), "the run delivers to the destination");
        assertEquals("42", observed.chatId());
        assertEquals(WorkloadContext.INTERACTIVE, observed.workload(), "an owner request is never batch work");
        assertTrue(observed.prompt().contains("stretch"), "the agent receives the owner's request");
        assertNull(delivery.memoryId(), "the delivery context is cleared after the run");
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> !ResultProbe.RESULTS.isEmpty());
        assertEquals(List.of(new Delivered("telegram", "42", "time to stretch")), ResultProbe.RESULTS);
    }

    @Test
    void aBlankResponseDeliversNothing() {
        String blank = tasks.create(MEMORY, TELEGRAM, "quiet", RECURRING).task().id();
        String answered = tasks.create(MEMORY, TELEGRAM, "loud", RECURRING).task().id();
        when(runner.chat(eq(MEMORY), anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(1);
            return prompt.contains("quiet") ? "   " : "hello";
        });

        tasks.fire(blank);
        tasks.fire(answered);

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> !ResultProbe.RESULTS.isEmpty());
        assertEquals(List.of(new Delivered("telegram", "42", "hello")), ResultProbe.RESULTS);
    }

    @Test
    void aScheduledTaskLogsItsDestinationCronAndNextRunButNotItsText() {
        try (CapturedLogs logs = new CapturedLogs()) {
            TaskView view = tasks.create(MEMORY, TELEGRAM, "bake the cake", RECURRING);

            String line = logs.info().stream().map(LogRecord::getMessage)
                    .filter(m -> m.startsWith("Scheduled agent task " + view.task().id()))
                    .findFirst().orElseThrow();
            assertTrue(line.contains("provider=telegram"), line);
            assertTrue(line.contains("chatId=42"), line);
            assertTrue(line.contains("cron=" + RECURRING), line);
            assertTrue(line.contains("nextFire=" + view.nextFire()), line);
            assertFalse(line.contains("bake the cake"), line);
        }
    }

    @Test
    void aFiredTaskLogsItsStartAndQueuedOutcomeButNotItsText() {
        String id = tasks.create(MEMORY, TELEGRAM, "bake the cake", RECURRING).task().id();
        when(runner.chat(eq(MEMORY), anyString())).thenReturn("cake is ready");

        try (CapturedLogs logs = new CapturedLogs()) {
            tasks.fire(id);

            List<String> lines = logs.info().stream().map(LogRecord::getMessage)
                    .filter(m -> m.contains(id)).toList();
            assertTrue(lines.stream().anyMatch(m -> m.equals("Job " + id + " started")), lines.toString());
            assertTrue(lines.stream().anyMatch(m -> m.startsWith("Job " + id + " finished")
                    && m.contains("destination=telegram") && m.contains("outcome=queued")), lines.toString());
            assertTrue(lines.stream().noneMatch(m -> m.contains("bake the cake")), lines.toString());
        }
    }

    @Test
    void aFiredTaskWithNoReplyLogsThatItDeliveredNothing() {
        String id = tasks.create(MEMORY, TELEGRAM, "stay quiet", RECURRING).task().id();
        when(runner.chat(eq(MEMORY), anyString())).thenReturn("  ");

        try (CapturedLogs logs = new CapturedLogs()) {
            tasks.fire(id);

            assertTrue(logs.info().stream().map(LogRecord::getMessage)
                    .anyMatch(m -> m.startsWith("Job " + id + " finished") && m.contains("outcome=no-reply")));
        }
    }

    @Test
    void aFailedFiredTaskLogsItsOutcomeByExceptionClassOnly() {
        String id = tasks.create(MEMORY, TELEGRAM, "pay the bill", RECURRING).task().id();
        when(runner.chat(eq(MEMORY), anyString())).thenThrow(new IllegalStateException("model unavailable at 10.0.0.7"));

        try (CapturedLogs logs = new CapturedLogs()) {
            tasks.fire(id);

            String failure = logs.warnings().stream().map(LogRecord::getMessage)
                    .filter(m -> m.startsWith("Job " + id + " failed"))
                    .findFirst().orElseThrow();
            assertTrue(failure.contains("error=IllegalStateException"), failure);
            assertFalse(failure.contains("10.0.0.7"), failure);
        }
    }

    @Test
    void aFiredTaskThatWasAlreadyCancelledDoesNothing() {
        String id = tasks.create(MEMORY, TELEGRAM, "stretch", RECURRING).task().id();
        assertTrue(tasks.cancel(id));

        assertDoesNotThrow(() -> tasks.fire(id));
    }

    @Test
    void aBlankOrOverlongTaskIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> tasks.create(MEMORY, TELEGRAM, "   ", RECURRING));
        String overlong = "x".repeat(DynamicTaskScheduler.MAX_TASK_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> tasks.create(MEMORY, TELEGRAM, overlong, RECURRING));
        assertEquals(0, tasks.list().size());
    }

    @Test
    void anInvalidCronIsRejectedAndNothingIsLeftScheduled() {
        assertThrows(IllegalArgumentException.class,
                () -> tasks.create(MEMORY, TELEGRAM, "stretch", "every tuesday at lunch"));
        assertEquals(0, tasks.list().size());
    }

    @Test
    void aCronThatNeverFiresAgainIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> tasks.create(MEMORY, TELEGRAM, "too late", "0 0 12 15 6 ? 2001"));
        assertEquals(0, tasks.list().size());
    }

    @Test
    void theNumberOfAgentTasksIsCapped() {
        for (int i = 0; i < DynamicTaskScheduler.MAX_TASKS; i++) {
            tasks.create(MEMORY, TELEGRAM, "task " + i, RECURRING);
        }

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> tasks.create(MEMORY, TELEGRAM, "one too many", RECURRING));

        assertTrue(refused.getMessage().contains(String.valueOf(DynamicTaskScheduler.MAX_TASKS)), refused.getMessage());
        assertEquals(DynamicTaskScheduler.MAX_TASKS, tasks.list().size());
    }

    @Test
    void aOneTimeCronForADelayIsAnExactDateInTheConfiguredZone() {
        String cron = tasks.cronInMinutes(120);

        assertTrue(DynamicTaskScheduler.isOneShot(cron), cron);
        TaskView view = tasks.create(MEMORY, TELEGRAM, "stretch", cron);
        long minutesAway = Duration.between(Instant.now(), view.nextFire()).toMinutes();
        assertTrue(minutesAway >= 118 && minutesAway <= 120, "fires about two hours from now: " + minutesAway);
    }

    @Test
    void cancelRemovesAnAgentTaskFromTheSchedulerAndTheList() {
        String id = tasks.create(MEMORY, TELEGRAM, "stretch", RECURRING).task().id();

        assertTrue(tasks.cancel(id));

        assertNull(scheduler.getScheduledJob(id));
        assertEquals(0, tasks.list().size());
    }

    @Test
    void cancelRefusesAnyIdTheAgentDidNotCreate() {
        String staticJob = scheduler.getScheduledJobs().stream()
                .map(trigger -> trigger.getId())
                .filter(id -> !id.startsWith(DynamicTaskScheduler.ID_PREFIX))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the app's own @Scheduled jobs are expected to be registered"));

        assertFalse(tasks.cancel(staticJob), "a static job is never cancelled through the agent's registry");
        assertNotNull(scheduler.getScheduledJob(staticJob));
        assertFalse(tasks.cancel(DynamicTaskScheduler.ID_PREFIX + UUID.randomUUID()), "an unknown id");
        assertFalse(tasks.cancel(null));
        assertFalse(tasks.cancel(""));
    }

    @Test
    void aDestinationMustNameAProviderAndAChat() {
        assertThrows(IllegalArgumentException.class, () -> new TaskDestination(" ", "42"));
        assertThrows(IllegalArgumentException.class, () -> new TaskDestination("telegram", null));
        String longChat = "c".repeat(DynamicTaskScheduler.MAX_DESTINATION_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> new TaskDestination("telegram", longChat));
    }

    record Seen(String memoryId, String providerId, String chatId, String workload, String prompt) {
    }

    record Delivered(String providerId, String chatId, String response) {
    }

    @ApplicationScoped
    static class ResultProbe {

        static final List<Delivered> RESULTS = new CopyOnWriteArrayList<>();

        void on(@ObservesAsync WorkflowResultReadyEvent event) {
            RESULTS.add(new Delivered(event.providerId(), event.chatId(), event.response()));
        }
    }
}
