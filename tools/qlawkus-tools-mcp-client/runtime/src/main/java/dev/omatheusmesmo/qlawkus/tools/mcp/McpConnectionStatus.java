package dev.omatheusmesmo.qlawkus.tools.mcp;
/** A bounded, non-sensitive view of a connection check. */
public record McpConnectionStatus(String id, McpConnectionState state, int toolCount) {}
