package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ShellToolTest {

    @Inject
    @ClawTool
    ShellTool shellTool;

    @Test
    void runCommand_success_returnsStdoutAndZeroExitCode() {
        String echoCmd = ShellTool.IS_WINDOWS ? "echo hello" : "echo hello";
        CommandResult result = shellTool.runCommand(echoCmd, null, null);

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
    void runCommand_timeout_killsProcess() {
        CommandResult result = shellTool.runCommand("sleep 60", null, 2);

        assertEquals(-1, result.exitCode(), "Exit code should be -1 on timeout");
        assertTrue(result.stderr().contains("timed out"), "stderr should mention timeout");
        assertTrue(result.durationMs() >= 1500, "Duration should be at least ~2s, was: " + result.durationMs());
        assertTrue(result.durationMs() < 10000, "Duration should be well under 10s, was: " + result.durationMs());
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
    void runCommand_customWorkdir_overridesDefault() {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        String printDirCmd = ShellTool.IS_WINDOWS ? "cd" : "pwd";
        CommandResult result = shellTool.runCommand(printDirCmd, tempDir.getAbsolutePath(), null);

        assertEquals(0, result.exitCode(), "Exit code should be 0");
        String output = result.stdout().trim();
        assertTrue(output.equals(tempDir.getAbsolutePath()) || output.contains(tempDir.getName()),
                "stdout should reference the temp dir, got: " + output);
    }

    @Test
    void runCommand_customTimeout_isRespected() {
        CommandResult result = shellTool.runCommand("sleep 10", null, 1);

        assertEquals(-1, result.exitCode(), "Exit code should be -1 on timeout");
        assertTrue(result.durationMs() < 5000, "Duration should be under 5s with 1s timeout, was: " + result.durationMs());
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
}
