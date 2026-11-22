package dev.omatheusmesmo.qlawkus.agent;

import java.util.UUID;

public record WorkflowResultReadyEvent(
        String providerId,
        String chatId,
        String response,
        String taskId,
        UUID runId
) {
    public WorkflowResultReadyEvent(String providerId, String chatId, String response) {
        this(providerId, chatId, response, null, null);
    }
}
