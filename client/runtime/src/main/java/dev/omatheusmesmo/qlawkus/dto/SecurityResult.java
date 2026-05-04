package dev.omatheusmesmo.qlawkus.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record SecurityResult(
        boolean blocked,
        String reason,
        String pattern,
        String command
) {
    @Override
    public String toString() {
        if (!blocked) {
            return "Security: ALLOWED — command is safe to execute";
        }
        return "Security: BLOCKED — " + reason + " (matched pattern: '" + pattern + "', command: '" + command + "')";
    }
}
