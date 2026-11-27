package dev.omatheusmesmo.qlawkus.permission;

import java.time.Instant;

/**
 * One recorded change of the policy.
 *
 * @param at when it was applied
 * @param actor who asked: an admin API user, or the chat account that sent the command
 * @param channel where it came from: {@code rest}, or the messaging provider
 * @param target what changed: {@code group.operation}, {@code default.operation}, {@code tool:name},
 *     {@code preset} or {@code reset}
 * @param before the level (or preset) before
 * @param after the level (or preset) after
 * @param approvedBy the owner decision that released it, when the request came from chat and loosened
 * @param requestId the approval request that carried it, when there was one
 */
public record PermissionAuditEntry(Instant at, String actor, String channel, String target, String before,
        String after, String approvedBy, String requestId) {
}
