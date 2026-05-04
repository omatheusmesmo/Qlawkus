package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.config.ShellConfig;
import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.dto.EnvironmentResult;
import dev.omatheusmesmo.qlawkus.dto.ProcessInfo;
import dev.omatheusmesmo.qlawkus.dto.SecurityResult;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@ClawTool
public class ShellTool {

    public static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    static final Set<String> WINDOWS_EXTENSIONS = Set.of(".exe", ".bat", ".cmd", ".ps1");

    static final int SECURITY_BLOCK_EXIT_CODE = -2;

    static final int CONCURRENT_LIMIT_EXIT_CODE = -3;

    @Inject
    ShellConfig shellConfig;

    String workspaceRoot;

    public int maxConcurrent;

    int maxOutputBytes;

    int maxOutputLines;

    @Inject
    CommandFilter commandFilter;

    @Inject
    WorkspaceConfinement workspaceConfinement;

    private final Map<Long, TrackedProcess> activeProcesses = new LinkedHashMap<>();
    private final AtomicInteger runningCount = new AtomicInteger(0);

    private volatile List<String> cachedPathCommands;

    @PostConstruct
    void init() {
        this.workspaceRoot = shellConfig.workspaceRoot();
        this.maxConcurrent = shellConfig.maxConcurrent();
        this.maxOutputBytes = shellConfig.maxOutputBytes();
        this.maxOutputLines = shellConfig.maxOutputLines();
        cachedPathCommands = scanPath();
        Log.infof("ShellTool: PATH scan found %d available commands", cachedPathCommands.size());
    }

    @Tool("Run a shell command and return structured results: stdout, stderr, exit code, and duration. " +
            "Parameters: command (required) — the shell command to execute, " +
            "workdir (optional) — working directory override (defaults to workspace root), " +
            "timeoutSeconds (optional) — execution timeout in seconds (omit to run indefinitely until completion)")
    public CommandResult runCommand(String command, String workdir, Integer timeoutSeconds) {
        long start = System.currentTimeMillis();

        SecurityResult commandCheck = commandFilter.check(command);
        if (commandCheck.blocked()) {
            Log.warnf("ShellTool: command blocked — %s", commandCheck);
            CommandResult result = blockedResult(commandCheck, start);
            auditLog(command, workdir, result.exitCode(), result.durationMs());
            return result;
        }

        SecurityResult pathCheck = workspaceConfinement.check(workdir);
        if (pathCheck.blocked()) {
            Log.warnf("ShellTool: workdir blocked — %s", pathCheck);
            CommandResult result = blockedResult(pathCheck, start);
            auditLog(command, workdir, result.exitCode(), result.durationMs());
            return result;
        }

        if (runningCount.get() >= maxConcurrent) {
            Log.warnf("ShellTool: concurrent limit reached (%d/%d) — rejecting '%s'",
                    runningCount.get(), maxConcurrent, command);
            CommandResult result = new CommandResult(
                    "", "Concurrent process limit reached (" + maxConcurrent + "). Wait for a process to finish or kill one with killProcess().",
                    CONCURRENT_LIMIT_EXIT_CODE, System.currentTimeMillis() - start, false);
            auditLog(command, workdir, result.exitCode(), result.durationMs());
            return result;
        }

        Path dir = workspaceConfinement.resolveCanonical(workdir);

        Log.debugf("ShellTool: executing '%s' in %s (timeout=%s)", command, dir,
                timeoutSeconds != null ? timeoutSeconds + "s" : "none");

        ProcessBuilder pb = new ProcessBuilder()
                .command(shellCommand(command))
                .directory(dir.toFile())
                .redirectErrorStream(false);

        Process process;
        try {
            runningCount.incrementAndGet();
            process = pb.start();
        } catch (IOException e) {
            runningCount.decrementAndGet();
            Log.errorf(e, "ShellTool: failed to start command '%s'", command);
            CommandResult result = new CommandResult("", "Failed to start command: " + e.getMessage(), -1,
                    System.currentTimeMillis() - start, false);
            auditLog(command, workdir, result.exitCode(), result.durationMs());
            return result;
        }

        TrackedProcess tracked = new TrackedProcess(process.pid(), command, start, "running");
        activeProcesses.put(process.pid(), tracked);

        OutputCapture stdoutCapture = new OutputCapture(process.getInputStream(), maxOutputBytes, maxOutputLines);
        OutputCapture stderrCapture = new OutputCapture(process.getErrorStream(), maxOutputBytes, maxOutputLines);
        stdoutCapture.start();
        stderrCapture.start();

        boolean finished;
        try {
            if (timeoutSeconds != null) {
                finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            } else {
                process.waitFor();
                finished = true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            tracked.status = "interrupted";
            runningCount.decrementAndGet();
            CommandResult result = new CommandResult(
                    stdoutCapture.getOutput(),
                    stderrCapture.getOutput() + "\n[Command interrupted]",
                    -1,
                    System.currentTimeMillis() - start,
                    stdoutCapture.isTruncated() || stderrCapture.isTruncated()
            );
            auditLog(command, workdir, result.exitCode(), result.durationMs());
            return result;
        }

        if (!finished) {
            process.destroyForcibly();
            tracked.status = "timed_out";
            runningCount.decrementAndGet();
            Log.warnf("ShellTool: command '%s' timed out after %ds", command, timeoutSeconds);
            CommandResult result = new CommandResult(
                    stdoutCapture.getOutput(),
                    stderrCapture.getOutput() + "\n[Command timed out after " + timeoutSeconds + "s]",
                    -1,
                    System.currentTimeMillis() - start,
                    stdoutCapture.isTruncated() || stderrCapture.isTruncated()
            );
            auditLog(command, workdir, result.exitCode(), result.durationMs());
            return result;
        }

        try {
            stdoutCapture.join(5000);
            stderrCapture.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        runningCount.decrementAndGet();
        int exitCode = process.exitValue();
        long duration = System.currentTimeMillis() - start;
        boolean truncated = stdoutCapture.isTruncated() || stderrCapture.isTruncated();

        tracked.status = "completed";
        Log.debugf("ShellTool: command '%s' finished with exitCode=%d in %dms", command, exitCode, duration);

        CommandResult result = new CommandResult(
                stdoutCapture.getOutput(),
                stderrCapture.getOutput(),
                exitCode,
                duration,
                truncated
        );
        auditLog(command, workdir, exitCode, duration);
        return result;
    }

    @Tool("Discover the execution environment: OS, default shell, workspace path, and available CLI tools. " +
            "Set refresh=true to rescan PATH for newly installed commands.")
    public EnvironmentResult listEnvironment(boolean refresh) {
        if (refresh) {
            cachedPathCommands = scanPath();
            Log.infof("ShellTool: PATH rescan found %d available commands", cachedPathCommands.size());
        }

        String os = System.getProperty("os.name", "unknown");
        String shell = IS_WINDOWS ? "cmd" : System.getenv().getOrDefault("SHELL", "sh");
        String workspace = workspaceConfinement.getWorkspacePath().toString();

        return new EnvironmentResult(os, shell, workspace, cachedPathCommands);
    }

    /**
     * Scans all directories in the PATH environment variable for executable files.
     * On Unix: files with execute permission. On Windows: files with known extensions (.exe, .bat, .cmd, .ps1).
     * Returns a sorted, deduplicated list of command names.
     */
    public List<String> scanPath() {
        Set<String> commands = new TreeSet<>();

        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return List.of();
        }

        String separator = IS_WINDOWS ? ";" : ":";
        for (String dir : pathEnv.split(separator)) {
            File directory = new File(dir);
            if (!directory.isDirectory()) {
                continue;
            }
            File[] files = directory.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                if (!file.isFile()) {
                    continue;
                }
                if (IS_WINDOWS) {
                    String name = file.getName().toLowerCase();
                    for (String ext : WINDOWS_EXTENSIONS) {
                        if (name.endsWith(ext)) {
                            commands.add(file.getName().substring(0, name.length() - ext.length()));
                            break;
                        }
                    }
                } else {
                    if (Files.isExecutable(file.toPath())) {
                        commands.add(file.getName());
                    }
                }
            }
        }

        return List.copyOf(commands);
    }

    @Tool("List processes spawned by the agent that are still tracked, with pid, command, and status")
    public List<ProcessInfo> listActiveProcesses() {
        List<ProcessInfo> processes = new ArrayList<>();

        for (TrackedProcess tp : activeProcesses.values()) {
            processes.add(new ProcessInfo(tp.pid, tp.command, tp.startedAtMs, tp.status));
        }

        Log.debugf("ShellTool: listActiveProcesses — %d tracked processes", processes.size());
        return processes;
    }

    @Tool("Kill a running process by PID. Use listActiveProcesses to find the PID first. " +
            "Parameter: pid (required) — the process ID to kill")
    public CommandResult killProcess(long pid) {
        TrackedProcess tracked = activeProcesses.get(pid);
        if (tracked == null) {
            return new CommandResult("", "No tracked process with PID " + pid, -1, 0, false);
        }

        try {
            ProcessHandle handle = ProcessHandle.of(pid).orElse(null);
            if (handle != null && handle.isAlive()) {
                handle.destroyForcibly();
                tracked.status = "killed";
                runningCount.decrementAndGet();
                Log.infof("ShellTool: killed process pid=%d command='%s'", pid, tracked.command);
                return new CommandResult("Process " + pid + " killed", "", 0, 0, false);
            } else {
                if ("running".equals(tracked.status)) {
                    runningCount.decrementAndGet();
                }
                tracked.status = "already_dead";
                return new CommandResult("Process " + pid + " was already terminated", "", 0, 0, false);
            }
        } catch (Exception e) {
            return new CommandResult("", "Failed to kill process " + pid + ": " + e.getMessage(), -1, 0, false);
        }
    }

    @Tool("Check whether a command and workdir are safe to execute before running them. " +
            "Returns SecurityResult with blocked=true/false, reason, and matched pattern. " +
            "Parameters: command (required) — the command to check, workdir (optional) — the working directory to check")
    public SecurityResult checkSecurity(String command, String workdir) {
        SecurityResult commandCheck = commandFilter.check(command);
        if (commandCheck.blocked()) {
            return commandCheck;
        }

        if (workdir != null && !workdir.isBlank()) {
            return workspaceConfinement.check(workdir);
        }

        return new SecurityResult(false, "", "", command);
    }

    /**
     * Checks if a specific command is available on PATH.
     * Uses native OS probe: {@code command -v} on Unix, {@code where} on Windows.
     */
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

    /**
     * Structured audit log for every command execution.
     * Format: {@code SHELL_CMD | cmd={} | workdir={} | exit={} | duration={}ms}
     */
    public void auditLog(String command, String workdir, int exitCode, long durationMs) {
        String workdirStr = workdir != null ? workdir : workspaceRoot;
        Log.infof("SHELL_CMD | cmd=%s | workdir=%s | exit=%d | duration=%dms",
                command, workdirStr, exitCode, durationMs);
    }

    public int getRunningCount() {
        return runningCount.get();
    }

    private CommandResult blockedResult(SecurityResult security, long start) {
        return new CommandResult(
                "",
                security.toString(),
                SECURITY_BLOCK_EXIT_CODE,
                System.currentTimeMillis() - start,
                false
        );
    }

    private Path resolveWorkdir(String workdir) {
        Path resolved = workspaceConfinement.resolveCanonical(workdir);
        return resolved != null ? resolved : Path.of(workspaceRoot).toAbsolutePath();
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
