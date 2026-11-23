package dev.omatheusmesmo.qlawkus.dto;

import dev.omatheusmesmo.qlawkus.approval.ApprovalRequest;

/**
 * The API view of one approval request. The arguments are shown only on the single-request endpoint:
 * a listing carries the bounded preview, so scanning the queue never prints a whole payload.
 */
public record ApprovalView(
        String id,
        String toolName,
        String preview,
        String status,
        String conversation,
        String createdAt,
        String expiresAt,
        String decidedAt,
        String decidedBy,
        String resultSummary,
        String arguments
) {

    public static ApprovalView summary(ApprovalRequest request) {
        return of(request, null);
    }

    public static ApprovalView detail(ApprovalRequest request) {
        return of(request, request.arguments());
    }

    private static ApprovalView of(ApprovalRequest request, String arguments) {
        return new ApprovalView(request.id(), request.toolName(), request.preview(), request.status().name(),
                request.memoryId(), String.valueOf(request.createdAt()), String.valueOf(request.expiresAt()),
                request.decidedAt() == null ? null : request.decidedAt().toString(), request.decidedBy(),
                request.resultSummary(), arguments);
    }
}
