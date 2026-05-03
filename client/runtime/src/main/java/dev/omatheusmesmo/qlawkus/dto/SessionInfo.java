package dev.omatheusmesmo.qlawkus.dto;

public record SessionInfo(String sessionId, String command, String status, long lastActivityMs) {
}
