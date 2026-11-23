package dev.omatheusmesmo.qlawkus.approval;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;

/**
 * The entry point the tool chain calls instead of running a gated tool. It records the request and
 * returns the text the model is given in place of the tool's result.
 */
public interface ApprovalGate {

    /** Records the call as pending and returns what the model should be told. Never runs the tool. */
    String requestApproval(String toolName, String rawArguments, String memoryId,
            AgentDeliveryContext.Snapshot origin);

    /** How many requests are waiting for the owner right now, for any surface that shows the queue. */
    int pendingCount();
}
