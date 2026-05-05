package dev.omatheusmesmo.qlawkus.dto;

import java.util.List;

public record SessionInfo(String sessionId, String command, String status, long lastActivityMs,
                          List<String> promptPatterns) {
}
