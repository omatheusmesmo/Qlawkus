package dev.omatheusmesmo.qlawkus.dto;

/**
 * The body of a request that sets one level: {@code allow}, {@code ask} or {@code deny}.
 */
public record PermissionLevelRequest(String level) {
}
