package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.dto.EnvironmentResult;
import dev.omatheusmesmo.qlawkus.dto.ProcessInfo;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@ClawTool
public class ShellTool {

    public static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    static final List<String> PROBED_COMMANDS = List.of(
            "git", "gh", "docker", "kubectl", "mvn", "gradle", "npm", "python3", "java", "curl", "wget", "ssh"
    );

    @ConfigProperty(name = "qlawkus.shell.default-timeout-seconds", defaultValue = "30")
    int defaultTimeoutSeconds;

    @ConfigProperty(name = "qlawkus.shell.workspace-root", defaultValue = ".")
    String workspaceRoot;

    private final Map<Long, TrackedProcess> activeProcesses = new LinkedHashMap<>();

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

        TrackedProcess tracked = new TrackedProcess(process.pid(), command, start, "running");
        activeProcesses.put(process.pid(), tracked);

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
            tracked.status = "interrupted";
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
            tracked.status = "timed_out";
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

        tracked.status = "completed";
        Log.debugf("ShellTool: command '%s' finished with exitCode=%d in %dms", command, exitCode, duration);

        return new CommandResult(
                stdoutCapture.getOutput(),
                stderrCapture.getOutput(),
                exitCode,
                duration,
                truncated
        );
    }

    @Tool("Discover the execution environment: OS, default shell, workspace path, and available CLI tools on PATH")
    public EnvironmentResult listEnvironment() {
        String os = System.getProperty("os.name", "unknown");
        String shell = IS_WINDOWS ? "cmd" : System.getenv().getOrDefault("SHELL", "sh");
        String workspace = Path.of(workspaceRoot).toAbsolutePath().toString();

        List<String> available = new ArrayList<>();
        for (String cmd : PROBED_COMMANDS) {
            if (isCommandAvailable(cmd)) {
                available.add(cmd);
            }
        }

        Log.debugf("ShellTool: listEnvironment — os=%s, shell=%s, workspace=%s, available=%s",
                os, shell, workspace, available);

        return new EnvironmentResult(os, shell, workspace, available);
    }

    @Tool("List processes spawned by the agent that are still tracked, with pid, command, and status")
    public List<ProcessInfo> listActiveProcesses() {
        List<ProcessInfo> processes = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (TrackedProcess tp : activeProcesses.values()) {
            processes.add(new ProcessInfo(tp.pid, tp.command, tp.startedAtMs, tp.status));
        }

        Log.debugf("ShellTool: listActiveProcesses — %d tracked processes", processes.size());
        return processes;
    }

    public boolean isCommandAvailable(String command) {
        String probeCmd = IS_WINDOWS ? "where " + command + " >nul 2>&1" : "command -v " + command;
        try {
            Process p = new ProcessBuilder()
                    .command(shellCommand(probeCmd))
                    .redirectErrorStream(true)
                    .start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
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

    static class TrackedProcess {
        final long pid;
        final String command;
        final long startedAtMs;
        volatile String status;

        TrackedProcess(long pid, String command, long startedAtMs, String status) {
            this.pid = pid;
            this.command = command;
            this.startedAtMs = startedAtMs;
            this.status = status;
        }
    }
}
