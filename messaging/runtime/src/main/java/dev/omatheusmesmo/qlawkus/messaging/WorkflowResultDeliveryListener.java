package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import dev.omatheusmesmo.qlawkus.task.ScheduledTaskFailedEvent;
import dev.omatheusmesmo.qlawkus.task.TaskAudit;
import dev.omatheusmesmo.qlawkus.task.TaskAuditEvent;
import dev.omatheusmesmo.qlawkus.task.TaskAuditEvent.Stage;
import dev.omatheusmesmo.qlawkus.task.TaskDestination;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import java.util.UUID;

@ApplicationScoped
public class WorkflowResultDeliveryListener {

    @Inject
    NotificationService notificationService;

    @Inject TaskAudit audit;

    void onWorkflowResultReady(@ObservesAsync WorkflowResultReadyEvent event) {
        Log.infof("WorkflowResultDeliveryListener: delivering workflow result provider=%s chatId=%s responseLen=%d",
                event.providerId(), event.chatId(), event.response().length());

        Stage stage;
        try {
            var report = notificationService.sendTracked(event.providerId(), event.chatId(), event.response(), null)
                    .await().indefinitely();
            stage = switch (report.status()) {
                case SENT -> Stage.DELIVERY_SENT;
                case PARTIAL -> Stage.DELIVERY_PARTIAL;
                case FAILED -> Stage.DELIVERY_FAILED;
                case NO_PROVIDER -> Stage.DELIVERY_NO_PROVIDER;
            };
            record(event.taskId(), event.runId(), event.providerId(), stage, report);
        } catch (RuntimeException failure) {
            String errorType = MessagingLog.exceptionName(failure);
            stage = Stage.DELIVERY_FAILED;
            recordFailure(event.taskId(), event.runId(), event.providerId(), stage, errorType);
            Log.warnf("Workflow result delivery failed provider=%s error=%s", event.providerId(), errorType);
        }
        if (event.runId() != null && stage != Stage.DELIVERY_SENT && stage != Stage.DELIVERY_NO_PROVIDER) {
            sendFailureNotice(event.taskId(), event.runId(), new TaskDestination(event.providerId(), event.chatId()),
                    "ran but its full reply could not be delivered");
        }
    }

    void onScheduledTaskFailed(@ObservesAsync ScheduledTaskFailedEvent event) {
        sendFailureNotice(event.taskId(), event.runId(), event.destination(), "failed while running");
    }

    private void sendFailureNotice(String taskId, UUID runId, TaskDestination destination, String reason) {
        String notice = "Scheduled task " + taskId + " " + reason + ".";
        try {
            var report = notificationService.sendTracked(destination.providerId(), destination.chatId(), notice, null)
                    .await().indefinitely();
            Stage stage = report.status() == NotificationService.DeliveryStatus.SENT
                    ? Stage.FAILURE_NOTICE_SENT : Stage.FAILURE_NOTICE_FAILED;
            record(taskId, runId, destination.providerId(), stage, report);
        } catch (RuntimeException failure) {
            String errorType = MessagingLog.exceptionName(failure);
            recordFailure(taskId, runId, destination.providerId(), Stage.FAILURE_NOTICE_FAILED, errorType);
            Log.warnf("Scheduled task failure notice could not be sent provider=%s error=%s",
                    destination.providerId(), errorType);
        }
    }

    private void record(String taskId, UUID runId, String provider, Stage stage,
                        NotificationService.DeliveryReport report) {
        if (runId != null) {
            audit.record(new TaskAuditEvent(taskId, runId, provider, stage, null, report.errorType(),
                    report.httpStatus(), report.acceptedParts(), report.totalParts()));
        }
    }

    private void recordFailure(String taskId, UUID runId, String provider, Stage stage, String errorType) {
        if (runId != null) {
            audit.record(new TaskAuditEvent(taskId, runId, provider, stage, null, errorType, null, null, null));
        }
    }
}
