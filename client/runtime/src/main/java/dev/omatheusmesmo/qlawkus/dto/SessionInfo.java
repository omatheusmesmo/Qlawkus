package dev.omatheusmesmo.qlawkus.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record SessionInfo(String sessionId, String command, String status, long lastActivityMs) {
}
