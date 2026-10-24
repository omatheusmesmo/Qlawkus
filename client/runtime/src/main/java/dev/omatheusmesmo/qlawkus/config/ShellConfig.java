package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.shell")
public interface ShellConfig {

    /**
     * Maximum number of simultaneously running processes.
     */
    @WithDefault("10")
    int maxConcurrent();

    /**
     * Default command timeout in seconds. Used when runCommand is called without explicit timeout.
     */
    @WithDefault("30")
    int defaultTimeout();

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
     * Names of the process environment variables a command may inherit; everything else is withheld,
     * so a command such as {@code env} cannot read the agent's own credentials. A trailing {@code *}
     * makes the entry a prefix match ({@code LC_*}). The workspace env file and
     * {@link #secretEnv()} are added on top of this list.
     */
    @WithDefault("PATH,HOME,USER,LANG,LC_*,TZ,TERM,SHELL,JAVA_HOME,TMPDIR")
    List<String> envAllowlist();

    /**
     * Secrets deliberately handed to commands, keyed by the environment variable name they appear
     * under, for example {@code qlawkus.shell.secret-env."GH_TOKEN"}. Empty by default. Values resolve
     * through normal configuration, so they can come from {@code .env} or from a keystore alias. A
     * blank value is skipped, which lets a distribution wire an entry to an environment variable
     * that may be unset. Every value is redacted from command output and from the audit log, and
     * these properties are never shown or written by the config editor. Passing {@code GH_TOKEN} also
     * configures git to authenticate to github.com through {@code gh}, with nothing written to disk.
     */
    Map<String, Optional<String>> secretEnv();

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
