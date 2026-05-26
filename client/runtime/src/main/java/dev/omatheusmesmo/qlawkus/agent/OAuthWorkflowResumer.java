package dev.omatheusmesmo.qlawkus.agent;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class OAuthWorkflowResumer {

    @Inject
    Instance<QlawkusAgentWorkflow> workflowInstance;

    @Inject
    Event<WorkflowResultReadyEvent> resultEvent;

    void onOAuthCallbackCompleted(@ObservesAsync OAuthCallbackCompletedEvent event) {
        if (workflowInstance.isUnsatisfied()) {
            Log.debug("OAuthWorkflowResumer: workflow not available, skipping resume");
            return;
        }

        Infrastructure.getDefaultWorkerPool().execute(() -> {
            ManagedContext requestContext = Arc.container().requestContext();
            boolean activated = false;
            if (!requestContext.isActive()) {
                requestContext.activate();
                activated = true;
            }
            try {
                resumeWorkflow(event);
            } finally {
                if (activated) {
                    requestContext.terminate();
                }
            }
        });
    }

    private void resumeWorkflow(OAuthCallbackCompletedEvent event) {
        QlawkusAgentWorkflow workflow = workflowInstance.get();
        String memoryId = event.memoryId();

        Log.infof("OAuthWorkflowResumer: resuming workflow for memoryId=%s", memoryId);

        boolean completed = workflow.completeOAuthApproval(memoryId, "approved");
        if (!completed) {
            Log.warnf("OAuthWorkflowResumer: no pending OAuth HITL to complete for memoryId=%s", memoryId);
            return;
        }

        if (event.providerId() == null || event.chatId() == null) {
            Log.infof("OAuthWorkflowResumer: no delivery info, workflow will resume on next user message");
            return;
        }

        try {
            String response = workflow.invoke(memoryId,
                    "Now that Google authorization is complete, please retry the last Google API request that failed.");
            Log.infof("OAuthWorkflowResumer: workflow resumed and completed for memoryId=%s, responseLen=%d",
                    memoryId, response != null ? response.length() : 0);

            if (response != null && !response.isBlank()) {
                resultEvent.fireAsync(new WorkflowResultReadyEvent(
                        event.providerId(), event.chatId(), response));
                Log.infof("OAuthWorkflowResumer: fired WorkflowResultReadyEvent for provider=%s chatId=%s",
                        event.providerId(), event.chatId());
            }
        } catch (Exception e) {
            Log.errorf(e, "OAuthWorkflowResumer: failed to resume workflow for memoryId=%s", memoryId);
        }
    }
}
