package dev.omatheusmesmo.qlawkus.task;

import java.time.Instant;

public record OwnerChat(String providerId, String chatId, Instant lastSeen) {
}
