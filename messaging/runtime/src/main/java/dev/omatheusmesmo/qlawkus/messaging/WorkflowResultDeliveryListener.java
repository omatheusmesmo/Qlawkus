package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import dev.omatheusmesmo.qlawkus.task.TaskAudit;
import dev.omatheusmesmo.qlawkus.task.TaskAuditEvent;
import dev.omatheusmesmo.qlawkus.task.TaskAuditEvent.Stage;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

@ApplicationScoped
public class WorkflowResultDeliveryListener {

    @Inject
    NotificationService notificationService;

    @Inject TaskAudit audit;

    void onWorkflowResultReady(@ObservesAsync WorkflowResultReadyEvent event) {
        Log.infof("WorkflowResultDeliveryListener: delivering workflow result provider=%s chatId=%s responseLen=%d",
                event.providerId(), event.chatId(), event.response().length());

        try {
            var report = notificationService.sendTracked(event.providerId(), event.chatId(), event.response(), null)
                    .await().indefinitely();
            Stage stage = switch (report.status()) {
                case SENT -> Stage.DELIVERY_SENT;
                case PARTIAL -> Stage.DELIVERY_PARTIAL;
                case FAILED -> Stage.DELIVERY_FAILED;
                case NO_PROVIDER -> Stage.DELIVERY_NO_PROVIDER;
            };
            record(event, stage, report.errorType(), report.httpStatus(), report.acceptedParts(), report.totalParts());
        } catch (RuntimeException failure) {
            String errorType = MessagingLog.exceptionName(failure);
            record(event, Stage.DELIVERY_FAILED, errorType, null, null, null);
            Log.warnf("Workflow result delivery failed provider=%s error=%s", event.providerId(), errorType);
        }
    }

    private void record(WorkflowResultReadyEvent event, Stage stage, String errorType, Integer status,
                        Integer accepted, Integer total) {
        if (event.runId() != null) {
            audit.record(new TaskAuditEvent(event.taskId(), event.runId(), event.providerId(), stage, null,
                    errorType, status, accepted, total));
        }
    }
}
