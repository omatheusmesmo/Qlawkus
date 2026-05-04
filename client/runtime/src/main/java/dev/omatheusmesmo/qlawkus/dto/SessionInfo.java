package dev.omatheusmesmo.qlawkus.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public record SessionInfo(String sessionId, String command, String status, long lastActivityMs,
                          List<String> promptPatterns) {
}
