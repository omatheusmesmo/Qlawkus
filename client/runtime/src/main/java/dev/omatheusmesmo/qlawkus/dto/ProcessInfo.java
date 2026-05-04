package dev.omatheusmesmo.qlawkus.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record ProcessInfo(
        long pid,
        String command,
        long startedAtMs,
        String status
) {
    @Override
    public String toString() {
        return "PID %d | %s | started %dms ago | %s".formatted(pid, command, System.currentTimeMillis() - startedAtMs, status);
    }
}
