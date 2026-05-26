package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

@ApplicationScoped
public class WorkflowResultDeliveryListener {

    @Inject
    NotificationService notificationService;

    void onWorkflowResultReady(@ObservesAsync WorkflowResultReadyEvent event) {
        Log.infof("WorkflowResultDeliveryListener: delivering workflow result provider=%s chatId=%s responseLen=%d",
                event.providerId(), event.chatId(), event.response().length());

        notificationService.send(event.providerId(), event.chatId(), event.response())
                .onFailure().invoke(err -> Log.errorf(err,
                        "WorkflowResultDeliveryListener: failed to deliver result provider=%s chatId=%s",
                        event.providerId(), event.chatId()))
                .onItem().invoke(() -> Log.infof("WorkflowResultDeliveryListener: result delivered provider=%s chatId=%s",
                        event.providerId(), event.chatId()))
                .await().indefinitely();
    }
}
