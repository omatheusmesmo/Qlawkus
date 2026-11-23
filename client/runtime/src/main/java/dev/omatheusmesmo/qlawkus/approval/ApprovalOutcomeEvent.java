package dev.omatheusmesmo.qlawkus.approval;

/** A decided request whose outcome the agent resumes from. Observed asynchronously. */
public record ApprovalOutcomeEvent(ApprovalRequest request) {
}
