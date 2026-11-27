package dev.omatheusmesmo.qlawkus.permission;

import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccessRegistry;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.nio.file.Path;

/**
 * The policy in force: the configuration layer, what the owner changed at runtime, and what extensions
 * suggest, resolved through a {@link PermissionMatrix}. It is the read side; {@link PermissionService}
 * is the only writer and installs each new runtime state here after persisting it.
 *
 * <p>It starts eagerly so a misspelt operation or an unreadable state file stops the boot instead of
 * surfacing at the first model turn.
 */
@Startup
@ApplicationScoped
public class PermissionPolicy implements ToolPermissions {

    private final PermissionState configured;
    private final Iterable<PermissionDefaults> extensions;
    private final ToolAccessRegistry registry;
    private volatile PermissionState runtime;
    private volatile PermissionMatrix matrix;

    @Inject
    public PermissionPolicy(PermissionsConfig config, AgentConfig agent, Instance<PermissionDefaults> extensions,
            ToolAccessRegistry registry) {
        this(PermissionConfigReader.read(config), extensions, registry,
                new PermissionStateFile(Path.of(agent.state().root(), PermissionStateFile.DIRECTORY)));
    }

    public PermissionPolicy(PermissionState configured, Iterable<PermissionDefaults> extensions,
            ToolAccessRegistry registry, PermissionStateFile file) {
        this.configured = configured;
        this.extensions = extensions;
        this.registry = registry;
        install(file.load());
    }

    @Override
    public PermissionLevel levelOf(String toolName) {
        return matrix.resolve(toolName).level();
    }

    public boolean isDenied(String toolName) {
        return levelOf(toolName) == PermissionLevel.DENY;
    }

    public PermissionMatrix.ToolResolution resolve(String toolName) {
        return matrix.resolve(toolName);
    }

    public PermissionMatrix matrix() {
        return matrix;
    }

    PermissionState configured() {
        return configured;
    }

    PermissionState runtime() {
        return runtime;
    }

    void install(PermissionState next) {
        this.runtime = next;
        this.matrix = new PermissionMatrix(configured, next, extensions, registry);
    }

    PermissionMatrix matrixFor(PermissionState candidate) {
        return new PermissionMatrix(configured, candidate, extensions, registry);
    }
}
