package dev.omatheusmesmo.qlawkus.permission;

import dev.omatheusmesmo.qlawkus.approval.ApprovalRequest;
import dev.omatheusmesmo.qlawkus.approval.OwnerAction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Carries a loosening that a chat sender asked for until the owner decides it. The approval service
 * calls {@link #run} only for a request the owner approved; this is not a tool, so it is never in the
 * catalog the model sees and nothing the model writes can name it.
 */
@ApplicationScoped
public class PermissionChangeAction implements OwnerAction {

    public static final String NAME = PREFIX + "permissions";

    private final PermissionService permissions;

    @Inject
    public PermissionChangeAction(PermissionService permissions) {
        this.permissions = permissions;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String run(ApprovalRequest request) {
        return permissions.applyApproved(request);
    }
}
