package dev.omatheusmesmo.qlawkus.approval;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.AgentRunner;
import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

/**
 * Resumes the conversation a decided request came from, so the agent can tell the owner what happened
 * or carry on. It runs for owner decisions only: an expiry or a boot-time review is reported to the chat
 * without a model turn, so nothing spends tokens while the owner is away.
 *
 * <p>The turn runs on the interactive workload, since the owner is waiting for it, and its reply goes to
 * the chat the request came from through the same event as every other agent reply.
 */
@ApplicationScoped
public class ApprovalResumeListener {

    @Inject
    AgentRunner agentRunner;

    @Inject
    AgentDeliveryContext deliveryContext;

    @Inject
    Event<WorkflowResultReadyEvent> resultEvent;

    void onOutcome(@ObservesAsync ApprovalOutcomeEvent event) {
        Infrastructure.getDefaultWorkerPool().execute(() -> withRequestContext(() -> resume(event.request())));
    }

    void resume(ApprovalRequest request) {
        if (request.providerId() == null || request.chatId() == null) {
            Log.infof("Approval request %s decided with no origin chat; the agent is not resumed", request.id());
            return;
        }
        AgentDeliveryContext.Snapshot previous = deliveryContext.snapshot();
        deliveryContext.set(request.memoryId(), request.providerId(), request.chatId());
        try {
            String response = agentRunner.chat(request.memoryId(), ApprovalResumePrompt.render(request));
            if (response != null && !response.isBlank()) {
                resultEvent.fireAsync(new WorkflowResultReadyEvent(request.providerId(), request.chatId(), response));
            }
            Log.infof("Approval request %s resumed the agent, responseLen=%d", request.id(),
                    response == null ? 0 : response.length());
        } catch (RuntimeException e) {
            Log.errorf(e, "Approval request %s could not resume the agent", request.id());
        } finally {
            deliveryContext.restore(previous);
        }
    }

    private static void withRequestContext(Runnable work) {
        ManagedContext requestContext = Arc.container().requestContext();
        boolean activated = false;
        if (!requestContext.isActive()) {
            requestContext.activate();
            activated = true;
        }
        try {
            work.run();
        } finally {
            if (activated) {
                requestContext.terminate();
            }
        }
    }
}
