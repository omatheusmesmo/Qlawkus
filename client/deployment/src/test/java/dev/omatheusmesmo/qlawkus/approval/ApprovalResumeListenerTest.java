package dev.omatheusmesmo.qlawkus.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.AgentRunner;
import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import jakarta.enterprise.event.Event;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * Runs the whole path from a decision to the resumed turn without a container: the service decides, its
 * notifier hands the outcome to the listener, and the listener asks the agent to continue in the chat
 * the request came from. Only the model call itself is a mock.
 */
class ApprovalResumeListenerTest {

    private static final Instant START = Instant.parse("2026-11-20T10:00:00Z");
    private static final String ARGS = "{\"to\":\"ana@example.com\"}";
    private static final AgentDeliveryContext.Snapshot ORIGIN =
            new AgentDeliveryContext.Snapshot("telegram:42", "telegram", "42");

    @TempDir
    Path root;

    private FileApprovalStore store;
    private AgentRunner agent;
    private Event<WorkflowResultReadyEvent> results;
    private AgentDeliveryContext delivery;
    private ApprovalResumeListener listener;
    private List<String> toolCalls;
    private List<String> deliveredDuringTurn;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        store = new FileApprovalStore(root, 100);
        agent = mock(AgentRunner.class);
        results = mock(Event.class);
        delivery = new AgentDeliveryContext();
        toolCalls = new ArrayList<>();
        deliveredDuringTurn = new ArrayList<>();
        listener = new ApprovalResumeListener();
        listener.agentRunner = agent;
        listener.deliveryContext = delivery;
        listener.resultEvent = results;
        when(agent.chat(any(), any())).thenAnswer(call -> {
            deliveredDuringTurn.add(delivery.providerId() + ":" + delivery.chatId());
            return "Sent it, and Ana has it.";
        });
    }

    @Test
    void anApprovedRequestResumesTheOriginalConversationExactlyOnceWithTheOutcome() {
        ApprovalService service = service();
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);
        String id = store.list().get(0).id();

        service.approve(id, "owner");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(agent, times(1)).chat(eq("telegram:42"), prompt.capture());
        assertTrue(prompt.getValue().contains("EXECUTED"), prompt.getValue());
        assertTrue(prompt.getValue().contains(id), prompt.getValue());
        assertEquals(List.of("telegram:42"), deliveredDuringTurn);
        assertFalse(delivery.hasDeliveryInfo(), "the resumed turn must not leave its delivery context behind");
        ArgumentCaptor<WorkflowResultReadyEvent> reply = ArgumentCaptor.forClass(WorkflowResultReadyEvent.class);
        verify(results, times(1)).fireAsync(reply.capture());
        assertEquals("telegram", reply.getValue().providerId());
        assertEquals("42", reply.getValue().chatId());
        assertEquals("Sent it, and Ana has it.", reply.getValue().response());
    }

    @Test
    void aDeniedRequestResumesTheConversationSoTheAgentCanReportIt() {
        ApprovalService service = service();
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);
        String id = store.list().get(0).id();

        service.deny(id, "owner");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(agent, times(1)).chat(eq("telegram:42"), prompt.capture());
        assertTrue(prompt.getValue().contains("did not run"), prompt.getValue());
        assertTrue(toolCalls.isEmpty());
    }

    @Test
    void aDuplicateApprovalNeverResumesTheConversationASecondTime() {
        ApprovalService service = service();
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);
        String id = store.list().get(0).id();

        service.approve(id, "owner");
        service.approve(id, "owner");

        verify(agent, times(1)).chat(any(), any());
        assertEquals(1, toolCalls.size());
    }

    @Test
    void aCallInterruptedByARestartIsReportedForReviewNeverReplayedNorResumed() {
        store.create(new ApprovalRequest("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "sendEmail",
                ApprovalBinding.canonicalArguments(ARGS),
                ApprovalBinding.hash("sendEmail", ApprovalBinding.canonicalArguments(ARGS)),
                "sendEmail " + ARGS, "telegram:42", "telegram", "42", ApprovalStatus.EXECUTING, START,
                START.plus(Duration.ofHours(24)), START, "owner", "running"));

        ApprovalService restarted = service();

        assertEquals(ApprovalStatus.NEEDS_REVIEW, store.find("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                .orElseThrow().status());
        assertTrue(toolCalls.isEmpty(), "an interrupted call is never run again");
        verify(agent, never()).chat(any(), any());
        assertEquals(ApprovalStatus.NEEDS_REVIEW, restarted.find("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                .orElseThrow().status());
    }

    @Test
    void aRequestThatCameFromNoChatResumesNothing() {
        ApprovalService service = service();
        service.requestApproval("sendEmail", ARGS, null, new AgentDeliveryContext.Snapshot(null, null, null));
        String id = store.list().get(0).id();

        service.approve(id, "owner");

        verify(agent, never()).chat(any(), any());
        assertEquals(1, toolCalls.size(), "the approved call still runs");
    }

    private ApprovalService service() {
        ApprovalNotifier notifier = new ApprovalNotifier() {
            @Override
            public void notifyOrigin(ApprovalRequest request, String text) {
            }

            @Override
            public void decided(ApprovalRequest request) {
                listener.resume(request);
            }
        };
        ApprovedToolExecutors tools = name -> Optional.of(new ToolExecutor() {
            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                toolCalls.add(request.name());
                return "sent";
            }
        });
        ApprovalService created = new ApprovalService(store, new FixedConfig(), tools, notifier, text -> text,
                Runnable::run, new AgentDeliveryContext(), AgentMeters.disabled(), Clock.fixed(START,
                        ZoneOffset.UTC));
        created.recover();
        return created;
    }

    private static final class FixedConfig implements ApprovalConfig {

        @Override
        public Optional<List<String>> tools() {
            return Optional.of(List.of("sendEmail"));
        }

        @Override
        public Duration expiry() {
            return Duration.ofHours(24);
        }

        @Override
        public int maxPending() {
            return 5;
        }

        @Override
        public int previewMaxChars() {
            return 200;
        }
    }
}
