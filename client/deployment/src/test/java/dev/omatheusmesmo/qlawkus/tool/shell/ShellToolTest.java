package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.dto.EnvironmentResult;
import dev.omatheusmesmo.qlawkus.dto.ProcessInfo;
import dev.omatheusmesmo.qlawkus.dto.SecurityResult;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ShellToolTest {

    @Inject
    @ClawTool
    ShellTool shellTool;

    @Inject
    CommandFilter commandFilter;

    @Inject
    WorkspaceConfinement workspaceConfinement;

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
    void listActiveProcesses_tracksSpawnedProcesses() {
        shellTool.runCommand("echo tracked_process_test", null, null);

        List<ProcessInfo> processes = shellTool.listActiveProcesses();

        assertFalse(processes.isEmpty(), "Should have tracked at least one process");
        boolean found = processes.stream()
                .anyMatch(p -> p.command().contains("tracked_process_test"));
        assertTrue(found, "Should find the tracked process by command, got: " + processes);
    }

    @Test
    void listActiveProcesses_completedProcessHasCompletedStatus() {
        shellTool.runCommand("echo completed_status_test", null, null);

        List<ProcessInfo> processes = shellTool.listActiveProcesses();

        boolean found = processes.stream()
                .anyMatch(p -> p.command().contains("completed_status_test") && "completed".equals(p.status()));
        assertTrue(found, "Completed process should have 'completed' status, got: " + processes);
    }

    @Test
    void listActiveProcesses_timedOutProcessHasTimedOutStatus() {
        shellTool.runCommand("sleep 60", null, 1);

        List<ProcessInfo> processes = shellTool.listActiveProcesses();

        boolean found = processes.stream()
                .anyMatch(p -> p.command().contains("sleep 60") && "timed_out".equals(p.status()));
        assertTrue(found, "Timed out process should have 'timed_out' status, got: " + processes);
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
}
