package dev.omatheusmesmo.qlawkus.approval;

/**
 * The owner prompt for a request that is waiting for a decision. A channel that can show buttons uses
 * {@code requestId} to offer Approve and Deny; one that cannot (or whose delivery fails) sends
 * {@code text}, which already tells the owner to reply {@code /approve <id>} or {@code /deny <id>}.
 * Both routes end at the same {@link ApprovalService}, behind the same allowlist.
 */
public record ApprovalPromptEvent(String providerId, String chatId, String requestId, String text) {
}
