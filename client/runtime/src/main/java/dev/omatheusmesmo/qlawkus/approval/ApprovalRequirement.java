package dev.omatheusmesmo.qlawkus.approval;

/**
 * Lets an extension put its own tools under the owner's approval without the owner listing them in
 * {@code qlawkus.approval.tools}. The Skill Hub and MCP extensions answer {@code true} for their write
 * tools while their approval mode is {@code hitl}, so the mode they already expose is enforced by the
 * durable gate instead of by a flag the model passes itself.
 */
public interface ApprovalRequirement {

    /** Whether a call to the {@code @QlawTool} tool with this name must wait for the owner. */
    boolean requires(String toolName);
}
