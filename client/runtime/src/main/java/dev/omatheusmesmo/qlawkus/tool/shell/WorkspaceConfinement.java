package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.config.WorkspaceConfig;
import dev.omatheusmesmo.qlawkus.dto.SecurityResult;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class WorkspaceConfinement {

    @Inject
    WorkspaceConfig workspaceConfig;

    /**
     * Workspace root directory. All path operations are confined to this directory and its subdirectories.
     * Default: {@code .} (current working directory where the Quarkus service runs).
     */
    String workspaceRoot;

    /**
     * Whether workspace confinement is active. When {@code false}, path checks always pass.
     */
    boolean restrictToWorkspace;

    /**
     * Environment variables loaded from {@code .qlawkus.env}.
     * Injected into all child processes spawned by ShellTool and PtySessionManager.
     */
    volatile Map<String, String> workspaceEnv = Collections.emptyMap();

    @PostConstruct
    void init() {
        this.workspaceRoot = workspaceConfig.root();
        this.restrictToWorkspace = workspaceConfig.restrictToWorkspace();
        Path workspace = Path.of(workspaceRoot).toAbsolutePath().normalize();
        validateWorkspace(workspace);
        ensureWorkspaceExists(workspace);
        loadEnv();
        Log.infof("WorkspaceConfinement: root=%s restrictToWorkspace=%s", workspace, restrictToWorkspace);
    }

    public SecurityResult check(String workdir) {
        if (!restrictToWorkspace) {
            return new SecurityResult(false, "", "", workdir);
        }

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

    public boolean isRestrictToWorkspace() {
        return restrictToWorkspace;
    }

    public void setRestrictToWorkspace(boolean value) {
        this.restrictToWorkspace = value;
    }

    public Path resolveCanonical(String pathStr) {
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

    /**
     * Returns the environment variables loaded from {@code .qlawkus.env}.
     * These are injected into all child processes.
     */
    public Map<String, String> getWorkspaceEnv() {
        return Collections.unmodifiableMap(workspaceEnv);
    }

    /**
     * Reloads environment variables from {@code .qlawkus.env} without restart.
     * Like {@code source ~/.bashrc} — cheap operation (reads one text file).
     */
    public Map<String, String> reloadEnv() {
        loadEnv();
        return getWorkspaceEnv();
    }

    void loadEnv() {
        Path envFile = getWorkspacePath().resolve(workspaceConfig.envFile());
        if (!Files.exists(envFile) || !Files.isRegularFile(envFile)) {
            Log.debugf("WorkspaceConfinement: no env file at %s", envFile);
            workspaceEnv = Collections.emptyMap();
            return;
        }

        Map<String, String> env = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(envFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                        value = value.substring(1, value.length() - 1);
                    } else if (value.startsWith("'") && value.endsWith("'") && value.length() > 1) {
                        value = value.substring(1, value.length() - 1);
                    }
                    env.put(key, value);
                }
            }
            workspaceEnv = Collections.unmodifiableMap(env);
            Log.infof("WorkspaceConfinement: loaded %d env vars from %s", env.size(), envFile);
        } catch (IOException e) {
            Log.warnf("WorkspaceConfinement: failed to read env file %s: %s", envFile, e.getMessage());
            workspaceEnv = Collections.emptyMap();
        }
    }

    void validateWorkspace(Path workspace) {
        String str = workspace.toString();
        if ("/".equals(str)) {
            throw new IllegalStateException("Workspace root cannot be '/'. Refusing to start with unrestricted access.");
        }
        String home = System.getProperty("user.home");
        if (home != null) {
            Path homePath = Path.of(home).toAbsolutePath().normalize();
            if (homePath.equals(workspace)) {
                throw new IllegalStateException("Workspace root cannot be the user home directory '" + home + "'. Use a subdirectory instead.");
            }
        }
    }

    void ensureWorkspaceExists(Path workspace) {
        if (!Files.exists(workspace)) {
            try {
                Files.createDirectories(workspace);
                Log.infof("WorkspaceConfinement: created workspace directory %s", workspace);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create workspace directory '" + workspace + "': " + e.getMessage(), e);
            }
        }
        if (!Files.isDirectory(workspace)) {
            throw new IllegalStateException("Workspace root '" + workspace + "' is not a directory.");
        }
        if (!Files.isWritable(workspace)) {
            throw new IllegalStateException("Workspace root '" + workspace + "' is not writable.");
        }
    }
}
