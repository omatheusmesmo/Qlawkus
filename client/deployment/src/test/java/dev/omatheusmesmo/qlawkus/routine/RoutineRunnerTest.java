package dev.omatheusmesmo.qlawkus.routine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.AgentRunner;
import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import dev.omatheusmesmo.qlawkus.task.OwnerChatDirectory;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@QuarkusTest
@TestProfile(RoutineRunnerTest.Enabled.class)
class RoutineRunnerTest {

    public static class Enabled implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("qlawkus.routines.weekly.enabled", "true", "qlawkus.routines.morning.enabled", "true",
                    "qlawkus.routines.morning.abandoned-after-days", "9");
        }
    }

    private static final OwnerChat TELEGRAM = new OwnerChat("telegram", "42", Instant.EPOCH);

    @Inject
    RoutineRunner routines;

    @Inject
    AgentDeliveryContext delivery;

    @InjectMock
    AgentRunner agent;

    @InjectMock
    OwnerChatDirectory owners;

    @BeforeEach
    void reset() {
        Delivered.ALL.clear();
        delivery.clear();
        when(owners.mostRecent()).thenReturn(Optional.of(TELEGRAM));
    }

    @Test
    void theWeeklyReportGoesToTheOwnersMostRecentChat() {
        when(agent.chat(anyString(), anyString())).thenReturn("Shipped three PRs.");

        assertEquals(RoutineOutcome.SENT, routines.runScheduled(Routine.WEEKLY));

        awaitDelivery();
        assertEquals(List.of("telegram|42|Shipped three PRs."), Delivered.ALL);
    }

    @Test
    void theModelIsAskedForTheRoutinesOwnPrompt() {
        when(agent.chat(anyString(), anyString())).thenReturn("ok");

        routines.runScheduled(Routine.MORNING);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(agent).chat(anyString(), prompt.capture());
        assertTrue(prompt.getValue().contains("Google Calendar"), prompt.getValue());
        assertTrue(prompt.getValue().contains("9 days"), "the configured threshold reaches the prompt");
    }

    @Test
    void theRunIsOnTheBatchWorkloadWithTheDestinationBoundAndClearedAfter() {
        AtomicReference<String> workload = new AtomicReference<>();
        AtomicReference<String> chat = new AtomicReference<>();
        when(agent.chat(anyString(), anyString())).thenAnswer(invocation -> {
            workload.set(WorkloadContext.current());
            chat.set(delivery.providerId() + ":" + delivery.chatId());
            return "report";
        });

        routines.runScheduled(Routine.WEEKLY);

        assertEquals(WorkloadContext.BATCH, workload.get(), "nobody is waiting on a routine");
        assertEquals("telegram:42", chat.get(), "the tools of the turn can see where the answer goes");
        assertNull(delivery.chatId(), "the thread is left clean");
    }

    @Test
    void withNoKnownChatTheAgentIsNotEvenAsked() {
        when(owners.mostRecent()).thenReturn(Optional.empty());

        assertEquals(RoutineOutcome.NO_DESTINATION, routines.runScheduled(Routine.WEEKLY));

        verify(agent, never()).chat(anyString(), anyString());
    }

    @Test
    void aFailedRunTellsTheOwnerWithoutLeakingTheErrorMessage() {
        when(agent.chat(anyString(), anyString())).thenThrow(new IllegalStateException("token sk-secret rejected"));

        assertEquals(RoutineOutcome.FAILED, routines.runScheduled(Routine.WEEKLY));

        awaitDelivery();
        String notice = Delivered.ALL.getFirst();
        assertTrue(notice.startsWith("telegram|42|"), notice);
        assertTrue(notice.contains("weekly report"), notice);
        assertTrue(notice.contains("IllegalStateException"), notice);
        assertFalse(notice.contains("sk-secret"), notice);
    }

    @Test
    void aBlankReplyIsReportedInsteadOfDroppedSilently() {
        when(agent.chat(anyString(), anyString())).thenReturn("  ");

        assertEquals(RoutineOutcome.NO_REPORT, routines.runScheduled(Routine.MORNING));

        awaitDelivery();
        assertTrue(Delivered.ALL.getFirst().contains("Monday briefing"), Delivered.ALL.getFirst());
    }

    @Test
    void aFailedRunIsNotRetried() {
        when(agent.chat(anyString(), anyString())).thenThrow(new IllegalStateException("down"));

        routines.runScheduled(Routine.WEEKLY);

        verify(agent, times(1)).chat(anyString(), anyString());
    }

    private static void awaitDelivery() {
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> !Delivered.ALL.isEmpty());
    }

    @ApplicationScoped
    static class Delivered {

        static final List<String> ALL = new CopyOnWriteArrayList<>();

        void on(@ObservesAsync WorkflowResultReadyEvent event) {
            ALL.add(event.providerId() + "|" + event.chatId() + "|" + event.response());
        }
    }
}
