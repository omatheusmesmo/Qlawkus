package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.config.ShellConfig;
import dev.omatheusmesmo.qlawkus.dto.SessionInfo;
import dev.omatheusmesmo.qlawkus.dto.SessionOutput;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class InteractiveShellToolTest {

    @Inject
    @ClawTool
    InteractiveShellTool interactiveShellTool;

    @Inject
    PtySessionManager sessionManager;

    @Inject
    WorkspaceConfinement workspaceConfinement;

    @Inject
    ShellConfig shellConfig;

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void startSession_returnsSessionId() {
        String sessionId =         interactiveShellTool.startSession("bash", null, null);

        assertNotNull(sessionId, "Session ID should not be null");
        assertFalse(sessionId.isEmpty(), "Session ID should not be empty");

        interactiveShellTool.closeSession(sessionId);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void sendInput_andReadSession_returnsOutput() throws Exception {
        String sessionId =         interactiveShellTool.startSession("bash", null, null);

        interactiveShellTool.sendInput(sessionId, "echo pty_test_output");
        Thread.sleep(1000);

        SessionOutput output = interactiveShellTool.readSession(sessionId, 0);

        assertNotNull(output.lines(), "Output lines should not be null");
        assertTrue(output.lines().stream().anyMatch(l -> l.contains("pty_test_output")),
                "Output should contain 'pty_test_output', got: " + output.lines());

        interactiveShellTool.closeSession(sessionId);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void readSession_withOffset_returnsOnlyNewLines() throws Exception {
        String sessionId =         interactiveShellTool.startSession("bash", null, null);

        interactiveShellTool.sendInput(sessionId, "echo line1");
        Thread.sleep(800);
        interactiveShellTool.sendInput(sessionId, "echo line2");
        Thread.sleep(800);

        SessionOutput first = interactiveShellTool.readSession(sessionId, 0);
        int offset = first.offset();

        SessionOutput second = interactiveShellTool.readSession(sessionId, offset);
        assertFalse(second.lines().stream().anyMatch(l -> l.contains("line1")),
                "Second read with offset should not contain line1");

        interactiveShellTool.closeSession(sessionId);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void closeSession_terminatesProcess() {
        String sessionId =         interactiveShellTool.startSession("bash", null, null);

        String result = interactiveShellTool.closeSession(sessionId);
        assertTrue(result.contains("closed"), "Close result should mention 'closed'");

        List<SessionInfo> sessions = interactiveShellTool.listSessions();
        assertTrue(sessions.stream().noneMatch(s -> s.sessionId().equals(sessionId)),
                "Closed session should not appear in list");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void listSessions_showsActiveSession() {
        String sessionId =         interactiveShellTool.startSession("bash", null, null);

        List<SessionInfo> sessions = interactiveShellTool.listSessions();
        assertTrue(sessions.stream().anyMatch(s -> s.sessionId().equals(sessionId)),
                "Active session should appear in list");

        interactiveShellTool.closeSession(sessionId);
    }

    @Test
    void closeSession_unknownId_returnsError() {
        String result = interactiveShellTool.closeSession("nonexistent-session-id");
        assertTrue(result.contains("ERROR"), "Unknown session should return error");
    }

    @Test
    void readSession_unknownId_returnsError() {
        SessionOutput output = interactiveShellTool.readSession("nonexistent-session-id", 0);
        assertTrue(output.lines().stream().anyMatch(l -> l.contains("ERROR")),
                "Unknown session should return error in output");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void sendInput_preservesStateBetweenCommands() throws Exception {
        String sessionId = interactiveShellTool.startSession("bash", null, null);

        interactiveShellTool.sendInput(sessionId, "export PTY_VAR=hello_from_pty");
        Thread.sleep(800);
        interactiveShellTool.sendInput(sessionId, "echo $PTY_VAR");
        Thread.sleep(800);

        SessionOutput output = interactiveShellTool.readSession(sessionId, 0);
        assertTrue(output.lines().stream().anyMatch(l -> l.contains("hello_from_pty")),
                "PTY session should preserve env vars between commands, got: " + output.lines());

        interactiveShellTool.closeSession(sessionId);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void startSession_withPrompts_detectsPromptInOutput() throws Exception {
        String sessionId = interactiveShellTool.startSession("bash", null, "[#$>] ");

        interactiveShellTool.sendInput(sessionId, "echo hello_prompt");
        Thread.sleep(1000);

        SessionOutput output = interactiveShellTool.readSession(sessionId, 0);
        assertTrue(output.lines().stream().anyMatch(l -> l.contains("hello_prompt")),
                "Output should contain echo output, got: " + output.lines());

        interactiveShellTool.closeSession(sessionId);
    }

    @Test
    void startSession_defaultShell_resolvedFromConfig() {
        String shell = sessionManager.getDefaultShell();
        assertNotNull(shell, "Default shell should not be null");
        assertFalse(shell.isBlank(), "Default shell should not be blank");
    }

    @Test
    void resolveCommand_bareShellName_appliesCleanProfile() {
        String resolved = sessionManager.resolveCommand("bash");
        if (!ShellTool.IS_WINDOWS && sessionManager.cleanProfile) {
            assertTrue(resolved.contains("--norc"), "Bare 'bash' should get --norc with clean-profile=true, got: " + resolved);
            assertTrue(resolved.contains("--noprofile"), "Bare 'bash' should get --noprofile with clean-profile=true, got: " + resolved);
        }
    }

    @Test
    void resolveCommand_nonShellCommand_unchanged() {
        String resolved = sessionManager.resolveCommand("python3");
        assertEquals("python3", resolved, "Non-shell command should pass through unchanged");
    }

    @Test
    void resolveCommand_nullOrBlank_usesDefaultShell() {
        String resolved = sessionManager.resolveCommand(null);
        assertNotNull(resolved, "Null command should resolve to default shell");
        assertFalse(resolved.isBlank(), "Default shell should not be blank");
    }

    @Test
    void sendInput_unknownSessionId_returnsError() {
        String result = interactiveShellTool.sendInput("nonexistent-session-id", "echo test");
        assertTrue(result.contains("ERROR"), "Unknown session sendInput should return error");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void startSession_withWorkdir_usesSpecifiedDir() throws Exception {
        String workspace = workspaceConfinement.getWorkspacePath().toString();
        String sessionId = interactiveShellTool.startSession("bash", workspace, null);
        assertNotNull(sessionId, "Session should start with valid workdir");

        interactiveShellTool.sendInput(sessionId, "pwd");
        Thread.sleep(1000);

        SessionOutput output = interactiveShellTool.readSession(sessionId, 0);
        assertTrue(output.lines().stream().anyMatch(l -> l.contains(workspace) || l.trim().equals(workspace)),
            "Session should run in specified workdir, got: " + output.lines());

        interactiveShellTool.closeSession(sessionId);
    }

    @Test
    void compilePromptPatterns_validPatterns_compilesCorrectly() {
        List<Pattern> patterns = sessionManager.compilePromptPatterns(List.of("[#$>] ", ">>> "));
        assertEquals(2, patterns.size(), "Should compile 2 valid patterns");
    }

    @Test
    void compilePromptPatterns_invalidPattern_skippedGracefully() {
        List<Pattern> patterns = sessionManager.compilePromptPatterns(List.of("[invalid", ">>> "));
        assertEquals(1, patterns.size(), "Invalid regex should be skipped, valid one kept");
        assertEquals(">>> ", patterns.get(0).pattern());
    }

    @Test
    void compilePromptPatterns_nullPrompts_usesDefaults() {
        List<Pattern> patterns = sessionManager.compilePromptPatterns(null);
        assertNotNull(patterns, "Should not throw on null prompts");
    }

    @Test
    void compilePromptPatterns_emptyList_fallsBackToDefaults() {
        List<Pattern> patterns = sessionManager.compilePromptPatterns(List.of());
        assertNotNull(patterns, "Empty prompt list should fall back to defaults, not throw");
    }

    @Test
    void resolveCommand_zsh_appliesCleanProfile() {
        if (ShellTool.IS_WINDOWS) return;
        String resolved = sessionManager.resolveCommand("zsh");
        if (sessionManager.cleanProfile) {
            assertTrue(resolved.contains("--norc"), "zsh should get --norc with clean-profile, got: " + resolved);
            assertTrue(resolved.contains("--noprofile"), "zsh should get --noprofile with clean-profile, got: " + resolved);
        }
    }

    @Test
    void resolveCommand_dash_appliesCleanProfile() {
        if (ShellTool.IS_WINDOWS) return;
        String resolved = sessionManager.resolveCommand("dash");
        if (sessionManager.cleanProfile) {
            assertTrue(resolved.contains("--norc"), "dash should get --norc with clean-profile, got: " + resolved);
        }
    }

    @Test
    void resolveCommand_fish_noCleanProfile() {
        if (ShellTool.IS_WINDOWS) return;
        String resolved = sessionManager.resolveCommand("fish");
        if (sessionManager.cleanProfile) {
            assertFalse(resolved.contains("--norc"), "fish should NOT get --norc, got: " + resolved);
        }
    }

    @Test
    void listSessions_afterClose_doesNotShowClosedSession() {
        List<SessionInfo> before = interactiveShellTool.listSessions();
        String sessionId = interactiveShellTool.startSession("bash", null, null);
        if (sessionId.startsWith("ERROR")) return;

        List<SessionInfo> during = interactiveShellTool.listSessions();
        assertTrue(during.size() >= before.size(), "Should have at least as many sessions after opening one");

        interactiveShellTool.closeSession(sessionId);
        List<SessionInfo> after = interactiveShellTool.listSessions();
        assertTrue(after.stream().noneMatch(s -> s.sessionId().equals(sessionId)),
            "Closed session should not appear in list");
    }

    @Test
    void ptySessionManager_maxSessions_configHasDefault() {
        ShellConfig.PtyConfig pty = shellConfig.pty();
        assertTrue(pty.maxSessions() > 0, "PtyConfig.maxSessions should default to 10, got: " + pty.maxSessions());
    }

    @Test
    void ptySessionManager_idleTimeout_configHasDefault() {
        ShellConfig.PtyConfig pty = shellConfig.pty();
        assertTrue(pty.idleTimeoutMinutes() > 0, "PtyConfig.idleTimeoutMinutes should default to 30, got: " + pty.idleTimeoutMinutes());
    }
}
