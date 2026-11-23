package dev.omatheusmesmo.qlawkus.approval;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * One gated tool call, persisted before anything runs. {@code arguments} is the canonical JSON the
 * approved call executes, and {@code bindingHash} is computed over the tool name and those arguments,
 * so a change to either after the owner was asked is detected before execution.
 *
 * <p>The record holds no credential: a call whose arguments look like one is refused before a
 * request is created.
 */
@RegisterForReflection
public record ApprovalRequest(
        String id,
        String toolName,
        String arguments,
        String bindingHash,
        String preview,
        String memoryId,
        String providerId,
        String chatId,
        ApprovalStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant decidedAt,
        String decidedBy,
        String resultSummary
) {

    private static final Pattern ID = Pattern.compile("[0-9a-f]{32}");

    /** Whether {@code candidate} has the shape of an id this store generates. */
    public static boolean isValidId(String candidate) {
        return candidate != null && ID.matcher(candidate).matches();
    }

    /** Moves to {@code next}, recording who decided and when. The result summary is kept. */
    public ApprovalRequest decide(ApprovalStatus next, Instant at, String by) {
        return new ApprovalRequest(id, toolName, arguments, bindingHash, preview, memoryId, providerId, chatId,
                next, createdAt, expiresAt, at, by, resultSummary);
    }

    /** Moves to {@code next} with a new bounded summary of what happened. */
    public ApprovalRequest settle(ApprovalStatus next, String summary) {
        return new ApprovalRequest(id, toolName, arguments, bindingHash, preview, memoryId, providerId, chatId,
                next, createdAt, expiresAt, decidedAt, decidedBy, summary);
    }
}
