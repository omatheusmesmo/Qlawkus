package dev.omatheusmesmo.qlawkus.approval;

import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.util.Locale;

/**
 * Delivers approval notices through CDI events. A request waiting for the owner becomes an
 * {@link ApprovalPromptEvent} so a channel can attach buttons; every other notice is plain chat output on
 * the same event the rest of the agent uses. The outcome of an {@link OwnerAction} is announced in the
 * chat without resuming the model, since there is no tool call for it to continue.
 */
@ApplicationScoped
public class CdiApprovalNotifier implements ApprovalNotifier {

    @Inject
    Event<WorkflowResultReadyEvent> resultEvent;

    @Inject
    Event<ApprovalPromptEvent> promptEvent;

    @Inject
    Event<ApprovalOutcomeEvent> outcomeEvent;

    @Override
    public void notifyOrigin(ApprovalRequest request, String text) {
        if (request.providerId() == null || request.chatId() == null) {
            return;
        }
        if (request.status() == ApprovalStatus.PENDING) {
            promptEvent.fireAsync(new ApprovalPromptEvent(request.providerId(), request.chatId(), request.id(), text));
            return;
        }
        resultEvent.fireAsync(new WorkflowResultReadyEvent(request.providerId(), request.chatId(), text));
    }

    @Override
    public void decided(ApprovalRequest request) {
        if (request.isOwnerAction()) {
            announce(request);
            return;
        }
        outcomeEvent.fireAsync(new ApprovalOutcomeEvent(request));
    }

    private void announce(ApprovalRequest request) {
        if (request.providerId() == null || request.chatId() == null) {
            return;
        }
        String text = switch (request.status()) {
            case EXECUTED -> request.resultSummary();
            case FAILED -> "The change was not applied: " + request.resultSummary();
            case DENIED -> "The change was denied and nothing changed.";
            default -> "The change is " + request.status().name().toLowerCase(Locale.ROOT) + ".";
        };
        resultEvent.fireAsync(new WorkflowResultReadyEvent(request.providerId(), request.chatId(), text));
    }
}
