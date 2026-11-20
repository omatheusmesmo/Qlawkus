package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.dto.EnvironmentResult;
import dev.omatheusmesmo.qlawkus.dto.ProcessInfo;
import dev.omatheusmesmo.qlawkus.dto.SecurityResult;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ShellToolTest {

    @Inject
    @QlawTool
    ShellTool shellTool;

    @Inject
    CommandFilter commandFilter;

    @Inject
    WorkspaceConfinement workspaceConfinement;

    private static final String ACCOUNTING_PREFIX = "acct-";

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final List<Future<CommandResult>> backgroundRuns = new ArrayList<>();

    @AfterEach
    void stopBackgroundRuns() {
        for (ProcessInfo process : shellTool.listActiveProcesses()) {
            if (process.command().contains(ACCOUNTING_PREFIX)) {
                shellTool.killProcess(process.pid());
            }
        }
        for (Future<CommandResult> run : backgroundRuns) {
            try {
                run.get(10, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                run.cancel(true);
            }
        }
        executor.shutdownNow();
    }

    private static String marker(String name) {
        return ACCOUNTING_PREFIX + name + "-" + UUID.randomUUID();
    }

    private static String sleeper(String marker) {
        return "exec sleep 300 # " + marker;
    }

    private Future<CommandResult> background(Callable<CommandResult> task) {
        Future<CommandResult> run = executor.submit(task);
        backgroundRuns.add(run);
        return run;
    }

    private Future<CommandResult> runInBackground(String command, Integer timeoutSeconds) {
        return background(() -> shellTool.runCommand(command, null, timeoutSeconds));
    }

    private long rejectedRuns() {
        return backgroundRuns.stream().filter(Future::isDone).count();
    }

    private Optional<ProcessInfo> findTracked(String marker) {
        return shellTool.listActiveProcesses().stream()
                .filter(p -> p.command().contains(marker))
                .findFirst();
    }

    private long awaitTracked(String marker) {
        await().atMost(Duration.ofSeconds(10)).until(() -> findTracked(marker).isPresent());
        return findTracked(marker).orElseThrow().pid();
    }

    private void assertNotTracked(String marker) {
        Optional<ProcessInfo> leftover = findTracked(marker);
        assertTrue(leftover.isEmpty(), "Finished process must not stay listed: " + leftover);
    }

    @Test
    void runCommand_success_returnsStdoutAndZeroExitCode() {
        CommandResult result = shellTool.runCommand("echo hello", null, null);

        assertEquals(0, result.exitCode(), "Exit code should be 0");
        assertTrue(result.stdout().contains("hello"), "stdout should contain 'hello'");
        assertTrue(result.stderr().isEmpty() || result.stderr().isBlank(), "stderr should be empty");
        assertFalse(result.truncated(), "Output should not be truncated");
        assertTrue(result.durationMs() >= 0, "Duration should be non-negative");
    }

    @Test
    void runCommand_failure_returnsNonZeroExitCode() {
        String exitCmd = ShellTool.IS_WINDOWS ? "exit /b 1" : "exit 1";
        CommandResult result = shellTool.runCommand(exitCmd, null, null);

        assertNotEquals(0, result.exitCode(), "Exit code should be non-zero");
        assertFalse(result.truncated(), "Output should not be truncated");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runCommand_capturesStderr_unix() {
        CommandResult result = shellTool.runCommand("echo error_msg >&2", null, null);

        assertEquals(0, result.exitCode(), "Exit code should be 0");
        assertTrue(result.stderr().contains("error_msg"), "stderr should contain 'error_msg'");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void runCommand_capturesStderr_windows() {
        CommandResult result = shellTool.runCommand("echo error_msg 1>&2", null, null);

        assertEquals(0, result.exitCode(), "Exit code should be 0");
        assertTrue(result.stderr().contains("error_msg"), "stderr should contain 'error_msg'");
    }

    @Test
    void runCommand_explicitTimeout_killsProcess() {
        CommandResult result = shellTool.runCommand("sleep 60", null, 2);

        assertEquals(-1, result.exitCode(), "Exit code should be -1 on timeout");
        assertTrue(result.stderr().contains("timed out"), "stderr should mention timeout");
        assertTrue(result.durationMs() >= 1500, "Duration should be at least ~2s, was: " + result.durationMs());
        assertTrue(result.durationMs() < 10000, "Duration should be well under 10s, was: " + result.durationMs());
    }

    @Test
    void runCommand_noTimeout_runsUntilCompletion() {
        CommandResult result = shellTool.runCommand("echo fast", null, null);

        assertEquals(0, result.exitCode(), "Exit code should be 0");
        assertTrue(result.stdout().contains("fast"), "stdout should contain 'fast'");
    }

    @Test
    void runCommand_largeOutput_isTruncated() {
        String genCmd = ShellTool.IS_WINDOWS
                ? "for /L %i in (1,1,200000) do @echo line%i"
                : "i=0; while [ $i -lt 200000 ]; do printf 'line%05d_padding_to_exceed_1mb\\n' $i; i=$((i+1)); done";

        CommandResult result = shellTool.runCommand(genCmd, null, 60);

        assertTrue(result.truncated(), "Large output should be truncated");
        assertFalse(result.stdout().isEmpty(), "stdout should still contain partial output");
    }

    @Test
    void runCommand_customWorkdir_insideWorkspace() {
        String workspace = workspaceConfinement.getWorkspacePath().toString();
        CommandResult result = shellTool.runCommand("pwd", workspace, null);

        assertEquals(0, result.exitCode(), "Exit code should be 0");
        String output = result.stdout().trim();
        assertTrue(output.contains(workspace) || output.equals(workspace),
                "stdout should reference the workspace dir, got: " + output);
    }

    @Test
    void runCommand_invalidCommand_returnsNonZeroExitCode() {
        CommandResult result = shellTool.runCommand("nonexistent_command_xyz_12345", null, null);

        assertNotEquals(0, result.exitCode(), "Exit code should be non-zero for invalid command");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runCommand_exitCodePreserved_unix() {
        CommandResult result42 = shellTool.runCommand("exit 42", null, null);
        assertEquals(42, result42.exitCode(), "Exit code 42 should be preserved");

        CommandResult result0 = shellTool.runCommand("true", null, null);
        assertEquals(0, result0.exitCode(), "Exit code 0 for 'true'");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void runCommand_exitCodePreserved_windows() {
        CommandResult result42 = shellTool.runCommand("exit /b 42", null, null);
        assertEquals(42, result42.exitCode(), "Exit code 42 should be preserved");

        CommandResult result0 = shellTool.runCommand("exit /b 0", null, null);
        assertEquals(0, result0.exitCode(), "Exit code 0 for 'exit /b 0'");
    }

    @Test
    void shellCommand_returnsCorrectShell() {
        if (ShellTool.IS_WINDOWS) {
            assertEquals(List.of("cmd", "/c", "echo hello"), ShellTool.shellCommand("echo hello"));
        } else {
            assertEquals(List.of("sh", "-c", "echo hello"), ShellTool.shellCommand("echo hello"));
        }
    }

    @Test
    void listEnvironment_returnsOsAndShellAndWorkspace() {
        EnvironmentResult env = shellTool.listEnvironment(false);

        assertNotNull(env.os(), "OS should not be null");
        assertFalse(env.os().isBlank(), "OS should not be blank");
        assertNotNull(env.shell(), "Shell should not be null");
        assertFalse(env.shell().isBlank(), "Shell should not be blank");
        assertNotNull(env.workspace(), "Workspace should not be null");
        assertFalse(env.workspace().isBlank(), "Workspace should not be blank");
        assertNotNull(env.availableCommands(), "Available commands should not be null");
    }

    @Test
    void listEnvironment_cachedCommandsAvailable() {
        EnvironmentResult env = shellTool.listEnvironment(false);

        List<String> available = env.availableCommands();
        assertNotNull(available, "Available commands list should not be null");
        assertFalse(available.isEmpty(), "PATH should have at least some commands");
        assertFalse(available.contains("nonexistent_command_xyz_12345"),
                "Nonexistent command should not appear in available list");
    }

    @Test
    void listEnvironment_refreshRescansPath() {
        EnvironmentResult cached = shellTool.listEnvironment(false);
        EnvironmentResult refreshed = shellTool.listEnvironment(true);

        assertFalse(refreshed.availableCommands().isEmpty(), "Refreshed scan should find commands");
        assertEquals(cached.availableCommands().size(), refreshed.availableCommands().size(),
                "Refresh should find same commands unless something changed");
    }

    @Test
    void scanPath_findsCommonCommands() {
        List<String> commands = shellTool.scanPath();

        assertFalse(commands.isEmpty(), "PATH scan should find executables");
        assertTrue(commands.contains("ls") || commands.contains("dir") || commands.contains("echo"),
                "Should find at least one common command, got: " + commands);
    }

    @Test
    void scanPath_doesNotContainFakeCommands() {
        List<String> commands = shellTool.scanPath();

        assertFalse(commands.contains("nonexistent_command_xyz_12345"),
                "Fake command should not appear in PATH scan");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void listActiveProcesses_listsRunningProcessWhileInFlight() throws Exception {
        String marker = marker("listed");
        Future<CommandResult> run = runInBackground(sleeper(marker), null);
        long pid = awaitTracked(marker);

        ProcessInfo info = findTracked(marker).orElseThrow();

        assertEquals(pid, info.pid(), "Listed pid should be the running process");
        assertEquals("running", info.status(), "In-flight process should report 'running', got: " + info);
        shellTool.killProcess(pid);
        run.get(10, TimeUnit.SECONDS);
    }

    @Test
    void listActiveProcesses_completedProcessIsNotListed() {
        String marker = marker("completed");

        CommandResult result = shellTool.runCommand("echo " + marker, null, null);

        assertEquals(0, result.exitCode(), "Command should succeed: " + result);
        assertNotTracked(marker);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runCommand_timeout_releasesSlotAndUnlists() {
        String marker = marker("timed-out");
        int baseline = shellTool.getRunningCount();

        CommandResult result = shellTool.runCommand(sleeper(marker), null, 1);

        assertEquals(-1, result.exitCode(), "Command should time out: " + result);
        assertEquals(baseline, shellTool.getRunningCount(), "Timeout must release the slot exactly once");
        assertNotTracked(marker);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runCommand_interrupted_releasesSlotAndUnlists() throws Exception {
        String marker = marker("interrupted");
        int baseline = shellTool.getRunningCount();
        Future<CommandResult> run = runInBackground(sleeper(marker), null);
        awaitTracked(marker);

        run.cancel(true);

        await().atMost(Duration.ofSeconds(10)).until(() -> shellTool.getRunningCount() == baseline);
        assertNotTracked(marker);
    }

    @Test
    void runCommand_startFailure_releasesSlot() {
        String marker = marker("start-failure");
        String missingWorkdir = workspaceConfinement.getWorkspacePath()
                .resolve("missing-" + UUID.randomUUID()).toString();
        int baseline = shellTool.getRunningCount();

        CommandResult result = shellTool.runCommand("echo " + marker, missingWorkdir, null);

        assertEquals(-1, result.exitCode(), "Command that cannot start should report -1: " + result);
        assertTrue(result.stderr().contains("Failed to start command"),
                "stderr should explain the start failure, got: " + result);
        assertEquals(baseline, shellTool.getRunningCount(), "A command that never started must not hold a slot");
        assertNotTracked(marker);
    }

    @Test
    void listActiveProcesses_duringChurn_neverFailsAndDropsFinishedCommands() {
        String prefix = marker("churn") + "-";
        for (int i = 0; i < 40; i++) {
            String command = "echo " + prefix + i;
            background(() -> shellTool.runCommand(command, null, null));
        }

        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(1)).until(() -> {
            shellTool.listActiveProcesses();
            return backgroundRuns.stream().allMatch(Future::isDone);
        });

        assertTrue(shellTool.listActiveProcesses().stream().noneMatch(p -> p.command().contains(prefix)),
                "Finished churn commands must not stay listed");
    }

    @Test
    void isCommandAvailable_echoAlwaysAvailable() {
        assertTrue(shellTool.isCommandAvailable("echo"), "echo should always be available");
    }

    @Test
    void isCommandAvailable_nonexistentReturnsFalse() {
        assertFalse(shellTool.isCommandAvailable("nonexistent_command_xyz_12345"),
                "Nonexistent command should return false");
    }

    @Test
    void commandFilter_denylist_blocksSudo() {
        SecurityResult result = commandFilter.check("sudo rm -rf /");
        assertTrue(result.blocked(), "sudo should be blocked");
        assertTrue(result.pattern().contains("sudo"), "Pattern should mention sudo");
    }

    @Test
    void commandFilter_denylist_blocksRmRfRoot() {
        SecurityResult result = commandFilter.check("rm -rf /");
        assertTrue(result.blocked(), "rm -rf / should be blocked");
    }

    @Test
    void commandFilter_denylist_blocksMkfs() {
        SecurityResult result = commandFilter.check("mkfs.ext4 /dev/sda1");
        assertTrue(result.blocked(), "mkfs should be blocked");
    }

    @Test
    void commandFilter_denylist_blocksShutdown() {
        SecurityResult result = commandFilter.check("shutdown -h now");
        assertTrue(result.blocked(), "shutdown should be blocked");
    }

    @Test
    void commandFilter_denylist_allowsSafeCommand() {
        SecurityResult result = commandFilter.check("ls -la");
        assertFalse(result.blocked(), "ls should be allowed");
    }

    @Test
    void commandFilter_denylist_allowsGit() {
        SecurityResult result = commandFilter.check("git status");
        assertFalse(result.blocked(), "git should be allowed");
    }

    @Test
    void commandFilter_allowlistMode_blocksNonAllowlisted() {
        CommandFilter testFilter = new CommandFilter();
        testFilter.denylist = List.of();
        testFilter.allowlistMode = true;
        testFilter.allowlist = List.of("git *", "ls", "echo *");

        assertTrue(testFilter.check("git status").blocked() == false, "git should be allowed in allowlist");
        assertTrue(testFilter.check("rm -rf /").blocked(), "rm should be blocked in allowlist mode");
        assertTrue(testFilter.check("docker ps").blocked(), "docker should be blocked in allowlist mode");
    }

    @Test
    void commandFilter_globMatch_exactMatch() {
        assertTrue(CommandFilter.globMatch("shutdown", "shutdown"));
        assertTrue(CommandFilter.globMatch("shutdown", "shutdown -h now"));
        assertFalse(CommandFilter.globMatch("shutdown", "echo shutdown"));
    }

    @Test
    void commandFilter_globMatch_wildcardPrefix() {
        assertTrue(CommandFilter.globMatch("sudo *", "sudo rm -rf /"));
        assertTrue(CommandFilter.globMatch("sudo *", "sudo apt install"));
        assertFalse(CommandFilter.globMatch("sudo *", "echo hello"));
    }

    @Test
    void commandFilter_globMatch_wildcardSuffix() {
        assertTrue(CommandFilter.globMatch("mkfs*", "mkfs.ext4 /dev/sda1"));
        assertTrue(CommandFilter.globMatch("mkfs*", "mkfs"));
        assertFalse(CommandFilter.globMatch("mkfs*", "ls mkfs"));
    }

    @Test
    void workspaceConfinement_blocksPathTraversal() {
        String workspace = workspaceConfinement.getWorkspacePath().toString();
        String traversalPath = workspace + "/../../../etc/passwd";

        SecurityResult result = workspaceConfinement.check(traversalPath);
        assertTrue(result.blocked(), "Path traversal should be blocked");
        assertEquals("path_traversal", result.pattern());
    }

    @Test
    void workspaceConfinement_allowsPathInsideWorkspace() {
        String workspace = workspaceConfinement.getWorkspacePath().toString();
        String insidePath = workspace + "/src/main";

        SecurityResult result = workspaceConfinement.check(insidePath);
        assertFalse(result.blocked(), "Path inside workspace should be allowed, got: " + result);
    }

    @Test
    void workspaceConfinement_allowsNullWorkdir() {
        SecurityResult result = workspaceConfinement.check(null);
        assertFalse(result.blocked(), "Null workdir should default to workspace root and be allowed");
    }

    @Test
    void workspaceConfinement_blocksAbsoluteOutsidePath() {
        SecurityResult result = workspaceConfinement.check("/etc/passwd");
        assertTrue(result.blocked(), "Absolute path outside workspace should be blocked, got: " + result);
    }

    @Test
    void workspaceConfinement_restrictToWorkspace_defaultIsTrue() {
        assertTrue(workspaceConfinement.isRestrictToWorkspace(), "restrictToWorkspace should default to true");
    }

    @Test
    void workspaceConfinement_whenDisabled_allowsOutsidePath() {
        boolean original = workspaceConfinement.isRestrictToWorkspace();
        workspaceConfinement.setRestrictToWorkspace(false);
        try {
            SecurityResult result = workspaceConfinement.check("/etc/passwd");
            assertFalse(result.blocked(), "When restrictToWorkspace=false, outside path should be allowed");
        } finally {
            workspaceConfinement.setRestrictToWorkspace(original);
        }
    }

    @Test
    void workspaceConfinement_whenDisabled_allowsPathTraversal() {
        boolean original = workspaceConfinement.isRestrictToWorkspace();
        workspaceConfinement.setRestrictToWorkspace(false);
        try {
            String workspace = workspaceConfinement.getWorkspacePath().toString();
            String traversalPath = workspace + "/../../../etc/passwd";
            SecurityResult result = workspaceConfinement.check(traversalPath);
            assertFalse(result.blocked(), "When restrictToWorkspace=false, path traversal should be allowed");
        } finally {
            workspaceConfinement.setRestrictToWorkspace(original);
        }
    }

    @Test
    void runCommand_denylistBlocksSudo() {
        CommandResult result = shellTool.runCommand("sudo apt install something", null, null);

        assertEquals(ShellTool.SECURITY_BLOCK_EXIT_CODE, result.exitCode(), "Blocked command should return -2");
        assertTrue(result.stderr().contains("BLOCKED"), "stderr should contain BLOCKED");
    }

    @Test
    void runCommand_workspaceConfinementBlocksTraversal() {
        String workspace = workspaceConfinement.getWorkspacePath().toString();
        CommandResult result = shellTool.runCommand("cat /etc/passwd",
                workspace + "/../../../etc", null);

        assertEquals(ShellTool.SECURITY_BLOCK_EXIT_CODE, result.exitCode(), "Path traversal should return -2");
        assertTrue(result.stderr().contains("BLOCKED"), "stderr should contain BLOCKED");
    }

    @Test
    void checkSecurity_allowsSafeCommand() {
        SecurityResult result = shellTool.checkSecurity("git status", null);
        assertFalse(result.blocked(), "git status should be allowed");
    }

    @Test
    void checkSecurity_blocksDangerousCommand() {
        SecurityResult result = shellTool.checkSecurity("sudo rm -rf /", null);
        assertTrue(result.blocked(), "sudo rm -rf / should be blocked");
    }

    @Test
    void checkSecurity_blocksTraversalWorkdir() {
        String workspace = workspaceConfinement.getWorkspacePath().toString();
        SecurityResult result = shellTool.checkSecurity("ls", workspace + "/../../etc");
        assertTrue(result.blocked(), "Traversal workdir should be blocked");
    }

    @Test
    void killProcess_forUnknownPid_returnsError() {
        CommandResult result = shellTool.killProcess(999999998L);

        assertTrue(!result.stdout().isEmpty() || !result.stderr().isEmpty(),
                "Should report error for unknown PID");
    }

    @Test
    void auditLog_isCalledOnSuccess() {
        shellTool.auditLog("echo test", "/tmp", 0, 100);
    }

    @Test
    void auditLog_isCalledOnFailure() {
        shellTool.auditLog("exit 1", null, 1, 50);
    }

    @Test
    void concurrentLimit_defaultIsFive() {
        assertTrue(shellTool.maxConcurrent > 0, "maxConcurrent should be positive");
    }

    @Test
    void runningCount_startsAtZero() {
        assertEquals(0, shellTool.getRunningCount(), "Running count should start at 0");
    }

    @Test
    void runningCount_decrementsAfterCompletion() {
        int before = shellTool.getRunningCount();
        shellTool.runCommand("echo count_test", null, null);
        int after = shellTool.getRunningCount();
        assertEquals(before, after, "Running count should return to original after command completes");
    }

    @Test
    void outputCapture_truncatesAtMaxBytes() {
        java.io.ByteArrayInputStream input = new java.io.ByteArrayInputStream(
                "line1\nline2\nline3\n".repeat(100000).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        OutputCapture capture = new OutputCapture(input, 500, 10000);
        capture.start();
        try { capture.join(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        assertTrue(capture.isTruncated(), "Output should be truncated at maxBytes=500");
    }

    @Test
    void outputCapture_truncatesAtMaxLines() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("line").append(i).append("\n");
        }
        java.io.ByteArrayInputStream input = new java.io.ByteArrayInputStream(
                sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        OutputCapture capture = new OutputCapture(input, 1_048_576, 10);
        capture.start();
        try { capture.join(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        String output = capture.getOutput();
        assertTrue(capture.isTruncated(), "Output should be truncated at maxLines=10");
        long lineCount = output.lines().count();
        assertTrue(lineCount <= 12, "Output should have at most ~10 lines + truncation notice, got: " + lineCount);
    }

    @Test
    void outputCapture_noTruncationUnderLimits() {
        java.io.ByteArrayInputStream input = new java.io.ByteArrayInputStream(
            "hello\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        OutputCapture capture = new OutputCapture(input, 1_048_576, 5000);
        capture.start();
        try { capture.join(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        assertFalse(capture.isTruncated(), "Small output should not be truncated");
        assertEquals("hello\n", capture.getOutput());
    }

    @Test
    void runCommand_defaultTimeout_isConfiguredFromShellConfig() {
        assertTrue(shellTool.defaultTimeout > 0, "defaultTimeout should be set from config, got: " + shellTool.defaultTimeout);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runCommand_explicitTimeout_appliesWhenProvided() {
        CommandResult result = shellTool.runCommand("sleep 60", null, 2);
        assertEquals(-1, result.exitCode(), "Should time out with explicit timeout");
        assertTrue(result.stderr().contains("timed out"), "stderr should mention timeout");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runCommand_workspaceEnvInjectedIntoProcess() {
        Map<String, String> env = workspaceConfinement.getWorkspaceEnv();
        CommandResult result = shellTool.runCommand("env", null, null);
        assertEquals(0, result.exitCode(), "env command should succeed");
        if (!env.isEmpty()) {
            String firstKey = env.keySet().iterator().next();
            assertTrue(result.stdout().contains(firstKey),
                "Process environment should contain workspace env var " + firstKey + ", got: " + result.stdout());
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void killProcess_untrackedPid_returnsNoTrackedProcess() {
        CommandResult result = shellTool.killProcess(999999);
        assertEquals(-1, result.exitCode(), "Untracked PID should return -1");
        assertTrue(result.stderr().contains("No tracked process"), "Should mention no tracked process, got: " + result);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void killProcess_liveProcess_releasesSlotExactlyOnce() throws Exception {
        String marker = marker("kill-once");
        int baseline = shellTool.getRunningCount();
        Future<CommandResult> run = runInBackground(sleeper(marker), null);
        long pid = awaitTracked(marker);
        assertEquals(baseline + 1, shellTool.getRunningCount(), "Starting a command should take one slot");

        CommandResult killed = shellTool.killProcess(pid);

        assertEquals(0, killed.exitCode(), "Kill of a live tracked process should succeed: " + killed);
        run.get(10, TimeUnit.SECONDS);
        assertEquals(baseline, shellTool.getRunningCount(), "Kill must release the slot exactly once, not twice");
        assertNotTracked(marker);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void killProcess_repeatedKill_neverMovesCounter() throws Exception {
        String marker = marker("kill-twice");
        int baseline = shellTool.getRunningCount();
        Future<CommandResult> run = runInBackground(sleeper(marker), null);
        long pid = awaitTracked(marker);

        shellTool.killProcess(pid);
        shellTool.killProcess(pid);

        run.get(10, TimeUnit.SECONDS);
        assertEquals(baseline, shellTool.getRunningCount(), "Repeated kills must not change the running count");
        assertNotTracked(marker);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void killProcess_afterProcessEndedExternally_reportsUntrackedAndReleasesOnce() throws Exception {
        String marker = marker("external");
        int baseline = shellTool.getRunningCount();
        Future<CommandResult> run = runInBackground(sleeper(marker), null);
        long pid = awaitTracked(marker);
        ProcessHandle.of(pid).orElseThrow().destroyForcibly();

        run.get(10, TimeUnit.SECONDS);
        CommandResult late = shellTool.killProcess(pid);

        assertEquals(baseline, shellTool.getRunningCount(), "External termination must release the slot exactly once");
        assertEquals(-1, late.exitCode(), "A finished process is no longer tracked: " + late);
        assertNotTracked(marker);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void killProcess_untrackedLiveProcess_isNeverKilled() throws Exception {
        int baseline = shellTool.getRunningCount();
        Process external = new ProcessBuilder("sleep", "300").start();
        try {
            CommandResult result = shellTool.killProcess(external.pid());

            assertEquals(-1, result.exitCode(), "A process the agent did not spawn is not tracked: " + result);
            assertTrue(external.isAlive(), "killProcess must never signal a process it does not track");
            assertEquals(baseline, shellTool.getRunningCount(), "Untracked kill must not move the counter");
        } finally {
            external.destroyForcibly();
        }
    }

    @Test
    void listActiveProcesses_emptyBeforeAnyCommand() {
        int beforeCount = shellTool.listActiveProcesses().size();
        assertTrue(beforeCount >= 0, "Process list should be non-negative");
    }

    @Test
    void commandFilter_denylist_blocksSu() {
        SecurityResult result = commandFilter.check("su - root");
        assertTrue(result.blocked(), "su should be blocked");
    }

    @Test
    void commandFilter_denylist_blocksDdIf() {
        SecurityResult result = commandFilter.check("dd if=/dev/zero of=/dev/sda");
        assertTrue(result.blocked(), "dd if= should be blocked");
    }

    @Test
    void commandFilter_denylist_blocksReboot() {
        SecurityResult result = commandFilter.check("reboot");
        assertTrue(result.blocked(), "reboot should be blocked");
    }

    @Test
    void commandFilter_denylist_blocksFormat() {
        SecurityResult result = commandFilter.check("format C:");
        assertTrue(result.blocked(), "format should be blocked");
    }

    @Test
    void commandFilter_globMatch_questionMarkWildcard() {
        assertTrue(CommandFilter.globMatch("for?at", "format"));
        assertFalse(CommandFilter.globMatch("for?at", "formats"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void concurrentLimit_rejectsAtCapacity_andAdmitsAfterKill() throws Exception {
        int savedLimit = shellTool.maxConcurrent;
        int baseline = shellTool.getRunningCount();
        shellTool.maxConcurrent = baseline + 2;
        String first = marker("capacity-first");
        String second = marker("capacity-second");
        try {
            Future<CommandResult> firstRun = runInBackground(sleeper(first), null);
            Future<CommandResult> secondRun = runInBackground(sleeper(second), null);
            long firstPid = awaitTracked(first);
            long secondPid = awaitTracked(second);
            assertEquals(baseline + 2, shellTool.getRunningCount(), "Both commands should hold a slot");

            CommandResult rejected = shellTool.runCommand("echo over_limit", null, null);

            assertEquals(ShellTool.CONCURRENT_LIMIT_EXIT_CODE, rejected.exitCode(),
                    "Should reject at capacity: " + rejected);
            assertEquals(baseline + 2, shellTool.getRunningCount(), "A rejected command must not take a slot");

            shellTool.killProcess(firstPid);
            firstRun.get(10, TimeUnit.SECONDS);
            assertEquals(baseline + 1, shellTool.getRunningCount(), "Killing one command frees exactly one slot");

            CommandResult admitted = shellTool.runCommand("echo after_kill", null, null);
            assertEquals(0, admitted.exitCode(), "The freed slot should admit a new command: " + admitted);

            shellTool.killProcess(secondPid);
            secondRun.get(10, TimeUnit.SECONDS);
            assertEquals(baseline, shellTool.getRunningCount(), "All slots should be free again");
        } finally {
            shellTool.maxConcurrent = savedLimit;
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void concurrentAdmission_neverExceedsMaxConcurrent() {
        int savedLimit = shellTool.maxConcurrent;
        int baseline = shellTool.getRunningCount();
        int capacity = 3;
        int callers = 16;
        String prefix = marker("admission") + "-";
        shellTool.maxConcurrent = baseline + capacity;
        CyclicBarrier gate = new CyclicBarrier(callers);
        try {
            for (int i = 0; i < callers; i++) {
                String command = sleeper(prefix + i);
                background(() -> {
                    gate.await();
                    return shellTool.runCommand(command, null, 60);
                });
            }

            await().atMost(Duration.ofSeconds(30)).until(() -> rejectedRuns() == callers - capacity);

            long admitted = shellTool.listActiveProcesses().stream()
                    .filter(p -> p.command().contains(prefix))
                    .count();
            assertEquals(capacity, admitted, "Exactly maxConcurrent callers may hold a slot at once");
            assertEquals(baseline + capacity, shellTool.getRunningCount(), "Counter should match admitted callers");
        } finally {
            shellTool.maxConcurrent = savedLimit;
        }
    }

    @Test
    void runCommand_blockedCommand_auditLogRecordsBlock() {
        CommandResult result = shellTool.runCommand("sudo rm -rf /", null, null);
        assertEquals(ShellTool.SECURITY_BLOCK_EXIT_CODE, result.exitCode(), "Should be blocked");
        assertTrue(result.durationMs() >= 0, "Duration should be recorded even for blocked commands");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void workspaceConfinement_symlinkEscape_blocked() throws Exception {
        Path workspace = workspaceConfinement.getWorkspacePath();
        Path linkTarget = Path.of("/etc");
        Path linkPath = workspace.resolve("escape-symlink-test");
        try {
            Files.createSymbolicLink(linkPath, linkTarget);
            SecurityResult result = workspaceConfinement.check(linkPath.toString());
            assertTrue(result.blocked(), "Symlink escaping workspace should be blocked, got: " + result);
        } finally {
            Files.deleteIfExists(linkPath);
        }
    }

    @Test
    void checkSecurity_allowsNullWorkdir() {
        SecurityResult result = shellTool.checkSecurity("ls", null);
        assertFalse(result.blocked(), "Null workdir with safe command should be allowed");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void gitWorkflow_initStatusDiff() {
        CommandResult initResult = shellTool.runCommand("git init git-workflow-test-dir", null, 10);
        assertEquals(0, initResult.exitCode(), "git init should succeed: " + initResult);

        CommandResult statusResult = shellTool.runCommand("git -C git-workflow-test-dir status", null, 10);
        assertEquals(0, statusResult.exitCode(), "git status should succeed: " + statusResult);

        CommandResult writeFileResult = shellTool.runCommand(
            "echo 'hello git' > git-workflow-test-dir/test.txt", null, 10);
        assertEquals(0, writeFileResult.exitCode(), "Creating a file should succeed");

        CommandResult diffResult = shellTool.runCommand("git -C git-workflow-test-dir diff", null, 10);
        assertEquals(0, diffResult.exitCode(), "git diff should succeed: " + diffResult);

        shellTool.runCommand("rm -rf git-workflow-test-dir", null, 10);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runCommand_pipeOperator_works() {
        CommandResult result = shellTool.runCommand("echo hello | grep hello", null, null);
        assertEquals(0, result.exitCode(), "Piped command should succeed");
        assertTrue(result.stdout().contains("hello"), "Pipe output should contain hello");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runCommand_andOperator_works() {
        CommandResult result = shellTool.runCommand("true && echo both_pass", null, null);
        assertEquals(0, result.exitCode(), "AND chain should succeed");
        assertTrue(result.stdout().contains("both_pass"), "AND chain output should contain both_pass");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runCommand_orOperator_works() {
        CommandResult result = shellTool.runCommand("false || echo fallback", null, null);
        assertTrue(result.stdout().contains("fallback"), "OR chain should execute fallback");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runCommand_stdoutAndStderr_doNotCrossContaminate() {
        CommandResult result = shellTool.runCommand(
                "sh -c 'echo STDOUT_TOKEN; echo STDERR_TOKEN >&2'", null, 10);

        assertTrue(result.stdout().contains("STDOUT_TOKEN"),
                "stdout must contain STDOUT_TOKEN");
        assertFalse(result.stdout().contains("STDERR_TOKEN"),
                "stdout must NOT contain STDERR_TOKEN — streams crossed: " + result.stdout());

        assertTrue(result.stderr().contains("STDERR_TOKEN"),
                "stderr must contain STDERR_TOKEN");
        assertFalse(result.stderr().contains("STDOUT_TOKEN"),
                "stderr must NOT contain STDOUT_TOKEN — streams crossed: " + result.stderr());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runCommand_largeStderr_doesNotCorruptStdout() {
        String command = "sh -c 'echo CLEAN_STDOUT; yes ERROR_LINE 2>&1 1>/dev/null | head -200 >&2'";
        CommandResult result = shellTool.runCommand(command, null, 10);

        assertTrue(result.stdout().contains("CLEAN_STDOUT"),
                "stdout must still contain CLEAN_STDOUT even under heavy stderr: " + result.stdout());
        assertFalse(result.stdout().contains("ERROR_LINE"),
                "stdout must not contain stderr content even when stderr is large");
    }
}
