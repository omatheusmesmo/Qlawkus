package dev.omatheusmesmo.qlawkus.approval;

import dev.omatheusmesmo.qlawkus.permission.PermissionLevel;
import dev.omatheusmesmo.qlawkus.permission.ToolPermissions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

/**
 * Decides which tools wait for the owner: the ones the permission matrix says {@code ask} for, the ones
 * named in {@code qlawkus.approval.tools}, and the ones an extension asks for through
 * {@link ApprovalRequirement}. The three only ever add to each other: a matrix {@code allow} never lifts
 * a tool the list or an extension named.
 */
@ApplicationScoped
public class ApprovalPolicy implements ApprovalReasons {

    private final ApprovalConfig config;
    private final Iterable<ApprovalRequirement> requirements;
    private final ToolPermissions permissions;

    @Inject
    public ApprovalPolicy(ApprovalConfig config, Instance<ApprovalRequirement> requirements,
            ToolPermissions permissions) {
        this(config, (Iterable<ApprovalRequirement>) requirements, permissions);
    }

    ApprovalPolicy(ApprovalConfig config, Iterable<ApprovalRequirement> requirements, ToolPermissions permissions) {
        this.config = config;
        this.requirements = requirements;
        this.permissions = permissions;
    }

    /** Whether a call to the named tool must be approved by the owner before it runs. */
    public boolean requires(String toolName) {
        return permissions.levelOf(toolName) == PermissionLevel.ASK || reasonFor(toolName).isPresent();
    }

    /** Why the tool waits for a reason other than the matrix: the approval list or an extension. */
    @Override
    public Optional<String> reasonFor(String toolName) {
        if (config.tools().orElse(List.of()).contains(toolName)) {
            return Optional.of("listed in qlawkus.approval.tools");
        }
        for (ApprovalRequirement requirement : requirements) {
            if (requirement.requires(toolName)) {
                return Optional.of("required by an extension");
            }
        }
        return Optional.empty();
    }
}
