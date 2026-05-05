package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.workspace")
public interface WorkspaceConfig {

    /**
     * Workspace root directory for file and shell operations.
     * Defaults to the current working directory ({@code .}) where the Quarkus service runs.
     * Must be writable and must not be {@code /} or the user home directory.
     */
    @WithDefault(".")
    String root();

    /**
     * When {@code true}, all file and shell operations are confined to the workspace root
     * and its subdirectories. Path traversal attempts are blocked.
     * Set to {@code false} to allow operations outside the workspace (not recommended).
     */
    @WithDefault("true")
    boolean restrictToWorkspace();

    /**
     * Name of the dotenv file in the workspace root.
     * Key-value pairs are loaded and injected as environment variables
     * in all child processes spawned by ShellTool and PtySessionManager.
     */
    @WithDefault(".qlawkus.env")
    String envFile();
}
