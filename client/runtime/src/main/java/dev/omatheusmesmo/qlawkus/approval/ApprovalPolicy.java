package dev.omatheusmesmo.qlawkus.approval;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Decides which tools wait for the owner: the ones named in {@code qlawkus.approval.tools} and the ones
 * an extension asks for through {@link ApprovalRequirement}.
 */
@ApplicationScoped
public class ApprovalPolicy {

    private final ApprovalConfig config;
    private final Iterable<ApprovalRequirement> requirements;

    @Inject
    public ApprovalPolicy(ApprovalConfig config, Instance<ApprovalRequirement> requirements) {
        this(config, (Iterable<ApprovalRequirement>) requirements);
    }

    ApprovalPolicy(ApprovalConfig config, Iterable<ApprovalRequirement> requirements) {
        this.config = config;
        this.requirements = requirements;
    }

    /** Whether a call to the named tool must be approved by the owner before it runs. */
    public boolean requires(String toolName) {
        if (config.tools().orElse(List.of()).contains(toolName)) {
            return true;
        }
        for (ApprovalRequirement requirement : requirements) {
            if (requirement.requires(toolName)) {
                return true;
            }
        }
        return false;
    }
}
