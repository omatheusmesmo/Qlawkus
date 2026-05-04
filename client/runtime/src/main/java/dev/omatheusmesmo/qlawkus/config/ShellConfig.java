package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.shell")
public interface ShellConfig {

    /**
     * Workspace root directory for shell command execution.
     */
    @WithDefault(".")
    String workspaceRoot();

    /**
     * Maximum number of simultaneously running processes.
     */
    @WithDefault("10")
    int maxConcurrent();

    /**
     * Maximum bytes captured per stream (stdout/stderr).
     */
    @WithDefault("1048576")
    int maxOutputBytes();

    /**
     * Maximum lines returned per stream.
     */
    @WithDefault("5000")
    int maxOutputLines();

    /**
     * Comma-separated list of denied command patterns.
     */
    @WithDefault("sudo *,su *,rm -rf /*,mkfs*,dd if=*,format,shutdown,reboot")
    List<String> denylist();

    /**
     * When true, switches to allowlist mode.
     */
    @WithDefault("false")
    boolean allowlistMode();

    /**
     * Comma-separated list of allowed command patterns.
     */
    @WithDefault("none")
    List<String> allowlist();

    /**
     * Default shell command. Use "auto" to detect from $SHELL.
     */
    @WithDefault("auto")
    String defaultShell();

    /**
     * Whether to append --norc --noprofile to bare shell names.
     */
    @WithDefault("true")
    boolean cleanProfile();

    /**
     * Regex patterns for shell prompt detection.
     */
    Optional<List<String>> prompts();

    /**
     * PTY session configuration.
     */
    PtyConfig pty();

    /**
     * PTY session settings.
     */
    interface PtyConfig {

        /**
         * Maximum number of concurrent PTY sessions.
         */
        @WithDefault("10")
        int maxSessions();

        /**
         * Idle timeout in minutes before session cleanup.
         */
        @WithDefault("30")
        int idleTimeoutMinutes();

        /**
         * Maximum lines retained in session output buffer.
         */
        @WithDefault("50000")
        int bufferLines();

        /**
         * Default terminal columns for new sessions.
         */
        @WithDefault("120")
        int defaultCols();

        /**
         * Default terminal rows for new sessions.
         */
        @WithDefault("40")
        int defaultRows();
    }
}
