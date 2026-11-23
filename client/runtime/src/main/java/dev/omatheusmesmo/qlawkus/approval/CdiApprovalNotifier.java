package dev.omatheusmesmo.qlawkus.approval;

import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

/**
 * Delivers approval notices through CDI events. A request waiting for the owner becomes an
 * {@link ApprovalPromptEvent} so a channel can attach buttons; every other notice is plain chat output on
 * the same event the rest of the agent uses.
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
        outcomeEvent.fireAsync(new ApprovalOutcomeEvent(request));
    }
}
