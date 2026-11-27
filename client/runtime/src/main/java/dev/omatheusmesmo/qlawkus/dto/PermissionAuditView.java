package dev.omatheusmesmo.qlawkus.dto;

import dev.omatheusmesmo.qlawkus.permission.PermissionAuditEntry;

/**
 * The API view of one recorded change of the permission policy.
 */
public record PermissionAuditView(String at, String actor, String channel, String target, String before,
        String after, String approvedBy, String requestId) {

    public static PermissionAuditView of(PermissionAuditEntry entry) {
        return new PermissionAuditView(entry.at().toString(), entry.actor(), entry.channel(), entry.target(),
                entry.before(), entry.after(), entry.approvedBy(), entry.requestId());
    }
}
