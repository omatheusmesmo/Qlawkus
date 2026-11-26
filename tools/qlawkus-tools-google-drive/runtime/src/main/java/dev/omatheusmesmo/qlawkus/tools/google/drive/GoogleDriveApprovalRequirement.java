package dev.omatheusmesmo.qlawkus.tools.google.drive;

import dev.omatheusmesmo.qlawkus.approval.ApprovalRequirement;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Puts {@code trashFile} behind the owner's durable approval while
 * {@code qlawkus.google.drive.trash-needs-approval} is on. Restoring a file and every read never wait.
 */
@ApplicationScoped
public class GoogleDriveApprovalRequirement implements ApprovalRequirement {

    static final String GATED_TOOL = "trashFile";

    private final GoogleDriveConfig config;

    @Inject
    public GoogleDriveApprovalRequirement(GoogleDriveConfig config) {
        this.config = config;
    }

    @Override
    public boolean requires(String toolName) {
        return config.enabled() && config.trashNeedsApproval() && GATED_TOOL.equals(toolName);
    }
}
