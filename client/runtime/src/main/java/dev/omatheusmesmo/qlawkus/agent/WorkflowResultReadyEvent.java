package dev.omatheusmesmo.qlawkus.agent;

public record WorkflowResultReadyEvent(
        String providerId,
        String chatId,
        String response
) {}
