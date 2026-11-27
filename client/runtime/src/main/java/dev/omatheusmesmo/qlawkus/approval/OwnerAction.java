package dev.omatheusmesmo.qlawkus.approval;

/**
 * An action only the owner can release, recorded and decided through the same approval request as a
 * gated tool call but never offered to the model as a tool. The approval service looks the name up here
 * before it looks for a tool, so a request for an owner action can only run its handler.
 *
 * <p>The model cannot create such a request: the approval gate refuses any tool call whose name starts
 * with {@link #PREFIX}.
 */
public interface OwnerAction {

    /** Marks a request as an owner action rather than a tool call. */
    String PREFIX = "owner-action:";

    /** The request name this handler answers to, starting with {@link #PREFIX}. */
    String name();

    /**
     * Carries out the approved request and returns a short summary of what happened. Throws to report
     * that it could not, which records the request as failed with the message.
     */
    String run(ApprovalRequest request);
}
