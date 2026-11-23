package dev.omatheusmesmo.qlawkus.tools.mcp;

import dev.omatheusmesmo.qlawkus.approval.ApprovalRequirement;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Puts {@code configureMcpConnection} behind the owner's durable approval while
 * {@code qlawkus.mcp.approval-mode} is {@code hitl}. Listing, testing and removing a connection never
 * wait, and in {@code yolo} nothing does.
 */
@ApplicationScoped
public class McpApprovalRequirement implements ApprovalRequirement {

    static final String CONFIGURE = "configureMcpConnection";

    private final McpConfig config;

    @Inject
    public McpApprovalRequirement(McpConfig config) {
        this.config = config;
    }

    @Override
    public boolean requires(String toolName) {
        return config.approvalMode() == McpConfig.ApprovalMode.HITL && CONFIGURE.equals(toolName);
    }
}
