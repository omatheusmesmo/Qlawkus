package dev.omatheusmesmo.qlawkus.agent;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

/**
 * After a Google OAuth callback completes, re-invokes the agent for the same conversation so it
 * retries the original request and delivers the result back to the chat. Relies on the agent's
 * persistent chat memory (keyed by memoryId) to recall what the user was doing.
 */
@ApplicationScoped
public class OAuthRetryListener {

    private static final String RETRY_PROMPT =
            "Google authorization is now complete. Please continue and complete the request "
            + "I was making before authorization was needed.";

    @Inject
    AgentRunner agentRunner;

    @Inject
    AgentDeliveryContext deliveryContext;

    @Inject
    Event<WorkflowResultReadyEvent> resultEvent;

    void onOAuthCallbackCompleted(@ObservesAsync OAuthCallbackCompletedEvent event) {
        Infrastructure.getDefaultWorkerPool().execute(() -> {
            ManagedContext requestContext = Arc.container().requestContext();
            boolean activated = false;
            if (!requestContext.isActive()) {
                requestContext.activate();
                activated = true;
            }
            try {
                resume(event);
            } finally {
                if (activated) {
                    requestContext.terminate();
                }
            }
        });
    }

    private void resume(OAuthCallbackCompletedEvent event) {
        String memoryId = event.memoryId();
        Log.infof("OAuthRetryListener: resuming agent for memoryId=%s", memoryId);

        if (event.providerId() == null || event.chatId() == null) {
            Log.infof("OAuthRetryListener: no delivery info, agent will resume on next user message");
            return;
        }

        deliveryContext.set(memoryId, event.providerId(), event.chatId());
        try {
            String response = agentRunner.chat(memoryId, RETRY_PROMPT);
            Log.infof("OAuthRetryListener: agent resumed for memoryId=%s, responseLen=%d",
                    memoryId, response != null ? response.length() : 0);

            if (response != null && !response.isBlank()) {
                resultEvent.fireAsync(new WorkflowResultReadyEvent(
                        event.providerId(), event.chatId(), response));
                Log.infof("OAuthRetryListener: fired WorkflowResultReadyEvent for provider=%s chatId=%s",
                        event.providerId(), event.chatId());
            }
        } catch (Exception e) {
            Log.errorf(e, "OAuthRetryListener: failed to resume agent for memoryId=%s", memoryId);
        } finally {
            deliveryContext.clear();
        }
    }
}
