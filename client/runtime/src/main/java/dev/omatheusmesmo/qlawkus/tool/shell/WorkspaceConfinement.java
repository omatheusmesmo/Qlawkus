package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.dto.SecurityResult;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WorkspaceConfinement {

    @ConfigProperty(name = "qlawkus.shell.workspace-root", defaultValue = ".")
    String workspaceRoot;

    public SecurityResult check(String workdir) {
        Path resolved = resolveCanonical(workdir);
        Path workspace = resolveCanonical(workspaceRoot);

        if (resolved == null || workspace == null) {
            Log.warnf("WorkspaceConfinement: cannot resolve paths — workdir='%s', workspace='%s'", workdir, workspaceRoot);
            return new SecurityResult(true, "Cannot resolve working directory", "path_resolution", workdir != null ? workdir : workspaceRoot);
        }

        if (!resolved.startsWith(workspace)) {
            Log.warnf("WorkspaceConfinement: path escapes workspace — workdir='%s' (resolved='%s'), workspace='%s'",
                    workdir, resolved, workspace);
            return new SecurityResult(true, "Working directory escapes workspace root", "path_traversal", workdir);
        }

        return new SecurityResult(false, "", "", workdir);
    }

    public Path getWorkspacePath() {
        Path resolved = resolveCanonical(workspaceRoot);
        return resolved != null ? resolved : Path.of(workspaceRoot).toAbsolutePath();
    }

    Path resolveCanonical(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) {
            return resolveCanonical(workspaceRoot);
        }
        try {
            return Path.of(pathStr).toAbsolutePath().toRealPath();
        } catch (IOException e) {
            try {
                return Path.of(pathStr).toAbsolutePath().normalize();
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
