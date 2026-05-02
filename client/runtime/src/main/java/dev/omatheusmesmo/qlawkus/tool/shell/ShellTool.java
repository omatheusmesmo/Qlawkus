package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ClawTool
public class ShellTool {

    public static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    @ConfigProperty(name = "qlawkus.shell.default-timeout-seconds", defaultValue = "30")
    int defaultTimeoutSeconds;

    @ConfigProperty(name = "qlawkus.shell.workspace-root", defaultValue = ".")
    String workspaceRoot;

    @Tool("Run a shell command and return structured results: stdout, stderr, exit code, and duration. " +
            "Parameters: command (required) — the shell command to execute, " +
            "workdir (optional) — working directory override (defaults to workspace root), " +
            "timeoutSeconds (optional) — execution timeout in seconds (defaults to 30)")
    public CommandResult runCommand(String command, String workdir, Integer timeoutSeconds) {
        Path dir = resolveWorkdir(workdir);
        int timeout = timeoutSeconds != null ? timeoutSeconds : defaultTimeoutSeconds;

        Log.debugf("ShellTool: executing '%s' in %s (timeout=%ds)", command, dir, timeout);

        ProcessBuilder pb = new ProcessBuilder()
                .command(shellCommand(command))
                .directory(dir.toFile())
                .redirectErrorStream(false);

        long start = System.currentTimeMillis();
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            Log.errorf(e, "ShellTool: failed to start command '%s'", command);
            return new CommandResult("", "Failed to start command: " + e.getMessage(), -1,
                    System.currentTimeMillis() - start, false);
        }

        OutputCapture stdoutCapture = new OutputCapture(process.getInputStream());
        OutputCapture stderrCapture = new OutputCapture(process.getErrorStream());
        stdoutCapture.start();
        stderrCapture.start();

        boolean finished;
        try {
            finished = process.waitFor(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new CommandResult(
                    stdoutCapture.getOutput(),
                    stderrCapture.getOutput() + "\n[Command interrupted]",
                    -1,
                    System.currentTimeMillis() - start,
                    stdoutCapture.isTruncated() || stderrCapture.isTruncated()
            );
        }

        if (!finished) {
            process.destroyForcibly();
            Log.warnf("ShellTool: command '%s' timed out after %ds", command, timeout);
            return new CommandResult(
                    stdoutCapture.getOutput(),
                    stderrCapture.getOutput() + "\n[Command timed out after " + timeout + "s]",
                    -1,
                    System.currentTimeMillis() - start,
                    stdoutCapture.isTruncated() || stderrCapture.isTruncated()
            );
        }

        try {
            stdoutCapture.join(5000);
            stderrCapture.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int exitCode = process.exitValue();
        long duration = System.currentTimeMillis() - start;
        boolean truncated = stdoutCapture.isTruncated() || stderrCapture.isTruncated();

        Log.debugf("ShellTool: command '%s' finished with exitCode=%d in %dms", command, exitCode, duration);

        return new CommandResult(
                stdoutCapture.getOutput(),
                stderrCapture.getOutput(),
                exitCode,
                duration,
                truncated
        );
    }

    private Path resolveWorkdir(String workdir) {
        if (workdir != null && !workdir.isBlank()) {
            return Path.of(workdir).toAbsolutePath();
        }
        return Path.of(workspaceRoot).toAbsolutePath();
    }

    public static List<String> shellCommand(String command) {
        if (IS_WINDOWS) {
            return List.of("cmd", "/c", command);
        }
        return List.of("sh", "-c", command);
    }
}
