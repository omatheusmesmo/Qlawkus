package dev.omatheusmesmo.qlawkus.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.AgentRunner;
import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.ScheduledTaskStore;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.Trigger;
import jakarta.enterprise.event.Event;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Pins how a task is registered with the scheduler, which the real-scheduler test cannot observe
 * directly: the cron, the configured zone, the overlap policy, and the cancel of a one-shot.
 */
class DynamicTaskSchedulerRegistrationTest {

    private static final String ZONE = "America/Sao_Paulo";
    private static final String CRON = "0 0 9 * * ? 2099";

    @Test
    void aTaskIsRegisteredWithItsCronInTheConfiguredZoneAndSkipsOverlappingRuns() {
        Scheduler scheduler = mock(Scheduler.class);
        Scheduler.JobDefinition<?> definition = mock(Scheduler.JobDefinition.class, RETURNS_SELF);
        Trigger trigger = mock(Trigger.class);
        when(trigger.getNextFireTime()).thenReturn(Instant.now().plus(Duration.ofDays(1)));
        doReturn(definition).when(scheduler).newJob(anyString());
        when(definition.schedule()).thenReturn(trigger);
        DynamicTaskScheduler tasks = new DynamicTaskScheduler(scheduler, runner(), delivery(), event(), config(),
                store());

        String id = tasks.create("telegram:42", new TaskDestination("telegram", "42"), "stand up", CRON).task().id();

        verify(scheduler).newJob(eq(id));
        verify(definition).setCron(CRON);
        verify(definition).setTimeZone(ZONE);
        verify(definition).setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP);
        verify(definition).schedule();
        assertTrue(id.startsWith(DynamicTaskScheduler.ID_PREFIX));
    }

    @Test
    void aTaskThatCannotBeScheduledLeavesNothingBehind() {
        Scheduler scheduler = mock(Scheduler.class);
        Scheduler.JobDefinition<?> definition = mock(Scheduler.JobDefinition.class, RETURNS_SELF);
        doReturn(definition).when(scheduler).newJob(anyString());
        when(definition.schedule()).thenThrow(new IllegalArgumentException("bad cron"));
        ScheduledTaskStore store = store();
        DynamicTaskScheduler tasks = new DynamicTaskScheduler(scheduler, runner(), delivery(), event(), config(),
                store);

        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> tasks.create("telegram:42", new TaskDestination("telegram", "42"), "stand up", CRON));
        assertTrue(rejected.getMessage().contains("cron"), rejected.getMessage());

        assertEquals(0, tasks.list().size());
        verify(store, never()).save(any());
    }

    @Test
    void aTaskWhoseRecordCannotBeSavedIsUnscheduledAndNotReported() {
        Scheduler scheduler = mock(Scheduler.class);
        Scheduler.JobDefinition<?> definition = mock(Scheduler.JobDefinition.class, RETURNS_SELF);
        Trigger trigger = mock(Trigger.class);
        when(trigger.getNextFireTime()).thenReturn(Instant.now().plus(Duration.ofDays(1)));
        doReturn(definition).when(scheduler).newJob(anyString());
        when(definition.schedule()).thenReturn(trigger);
        ScheduledTaskStore store = store();
        doThrow(new UncheckedIOException(new IOException("disk full"))).when(store).save(any());
        DynamicTaskScheduler tasks = new DynamicTaskScheduler(scheduler, runner(), delivery(), event(), config(),
                store);

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> tasks.create("telegram:42", new TaskDestination("telegram", "42"), "stand up", CRON));

        assertTrue(refused.getMessage().contains("not scheduled"), refused.getMessage());
        verify(scheduler).unscheduleJob(argThat(id -> id.startsWith(DynamicTaskScheduler.ID_PREFIX)));
        assertEquals(0, tasks.list().size());
    }

    @Test
    void aCancelThatCannotRemoveTheRecordKeepsTheTaskScheduled() {
        Scheduler scheduler = mock(Scheduler.class);
        Scheduler.JobDefinition<?> definition = mock(Scheduler.JobDefinition.class, RETURNS_SELF);
        Trigger trigger = mock(Trigger.class);
        when(trigger.getNextFireTime()).thenReturn(Instant.now().plus(Duration.ofDays(1)));
        doReturn(definition).when(scheduler).newJob(anyString());
        when(definition.schedule()).thenReturn(trigger);
        ScheduledTaskStore store = store();
        DynamicTaskScheduler tasks = new DynamicTaskScheduler(scheduler, runner(), delivery(), event(), config(),
                store);
        String id = tasks.create("telegram:42", new TaskDestination("telegram", "42"), "stand up", CRON).task().id();
        doThrow(new UncheckedIOException(new IOException("read only"))).when(store).delete(id);

        assertThrows(IllegalStateException.class, () -> tasks.cancel(id));

        assertEquals(1, tasks.list().size(), "a task that still has its record is still reported");
        verify(scheduler, never()).unscheduleJob(id);
    }

    private static ScheduledTaskStore store() {
        return mock(ScheduledTaskStore.class);
    }

    private static AgentRunner runner() {
        return mock(AgentRunner.class);
    }

    private static AgentDeliveryContext delivery() {
        return new AgentDeliveryContext();
    }

    @SuppressWarnings("unchecked")
    private static Event<WorkflowResultReadyEvent> event() {
        return mock(Event.class);
    }

    private static AgentConfig config() {
        AgentConfig config = mock(AgentConfig.class);
        when(config.timezone()).thenReturn(ZONE);
        return config;
    }
}
