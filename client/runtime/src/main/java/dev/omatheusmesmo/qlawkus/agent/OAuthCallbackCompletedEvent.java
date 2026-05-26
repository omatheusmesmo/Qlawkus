package dev.omatheusmesmo.qlawkus.agent;

public record OAuthCallbackCompletedEvent(
        String refreshToken,
        String memoryId,
        String providerId,
        String chatId
) {}
