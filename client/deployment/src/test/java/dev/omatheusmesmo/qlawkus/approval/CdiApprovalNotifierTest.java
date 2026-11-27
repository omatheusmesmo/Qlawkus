package dev.omatheusmesmo.qlawkus.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import jakarta.enterprise.event.Event;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CdiApprovalNotifierTest {

    private static final String ID = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    private CdiApprovalNotifier notifier;
    private Event<WorkflowResultReadyEvent> results;
    private Event<ApprovalPromptEvent> prompts;
    private Event<ApprovalOutcomeEvent> outcomes;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        notifier = new CdiApprovalNotifier();
        results = mock(Event.class);
        prompts = mock(Event.class);
        outcomes = mock(Event.class);
        notifier.resultEvent = results;
        notifier.promptEvent = prompts;
        notifier.outcomeEvent = outcomes;
    }

    @Test
    void aRequestWaitingForTheOwnerIsOfferedAsAPromptNotAsPlainText() {
        notifier.notifyOrigin(request(ApprovalStatus.PENDING, "telegram", "42"), "Approval needed");

        ArgumentCaptor<ApprovalPromptEvent> prompt = ArgumentCaptor.forClass(ApprovalPromptEvent.class);
        verify(prompts).fireAsync(prompt.capture());
        assertEquals(
                new ApprovalPromptEvent("telegram", "42", ID, "Approval needed"), prompt.getValue());
        verify(results, never()).fireAsync(any());
    }

    @Test
    void aSettledRequestNoticeIsPlainTextWithNoButtons() {
        notifier.notifyOrigin(request(ApprovalStatus.EXPIRED, "telegram", "42"), "It expired");

        verify(results).fireAsync(any(WorkflowResultReadyEvent.class));
        verify(prompts, never()).fireAsync(any());
    }

    @Test
    void aRequestWithNoChatToAnswerNotifiesNobody() {
        notifier.notifyOrigin(request(ApprovalStatus.PENDING, null, null), "Approval needed");

        verify(results, never()).fireAsync(any());
        verify(prompts, never()).fireAsync(any());
    }

    @Test
    void theOutcomeOfAnOwnerActionIsAnnouncedInTheChatAndNeverResumesTheModel() {
        ApprovalRequest executed = new ApprovalRequest(ID, OwnerAction.PREFIX + "permissions", "{}", "hash",
                "Permission change", null, "telegram", "42", ApprovalStatus.EXECUTED, NOW, NOW.plusSeconds(60), NOW,
                "owner", "calendar delete is now ask");

        notifier.decided(executed);

        ArgumentCaptor<WorkflowResultReadyEvent> text = ArgumentCaptor.forClass(WorkflowResultReadyEvent.class);
        verify(results).fireAsync(text.capture());
        assertEquals("telegram", text.getValue().providerId());
        assertTrue(text.getValue().response().contains("calendar delete is now ask"));
        verify(outcomes, never()).fireAsync(any());
    }

    @Test
    void theOutcomeOfAToolCallStillResumesTheModel() {
        notifier.decided(request(ApprovalStatus.EXECUTED, "telegram", "42"));

        verify(outcomes).fireAsync(any(ApprovalOutcomeEvent.class));
        verify(results, never()).fireAsync(any());
    }

    private static ApprovalRequest request(ApprovalStatus status, String provider, String chat) {
        return new ApprovalRequest(ID, "sendEmail", "{}", "hash", "sendEmail {}", "telegram:42", provider, chat,
                status, NOW, NOW.plusSeconds(60), NOW, null, null);
    }
}
