package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.dto.SessionInfo;
import dev.omatheusmesmo.qlawkus.dto.SessionOutput;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class InteractiveShellToolTest {

    @Inject
    @ClawTool
    InteractiveShellTool interactiveShellTool;

    @Inject
    PtySessionManager sessionManager;

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void startSession_returnsSessionId() {
        String sessionId = interactiveShellTool.startSession("bash", null);

        assertNotNull(sessionId, "Session ID should not be null");
        assertFalse(sessionId.isEmpty(), "Session ID should not be empty");

        interactiveShellTool.closeSession(sessionId);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void sendInput_andReadSession_returnsOutput() throws Exception {
        String sessionId = interactiveShellTool.startSession("bash", null);

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
        String sessionId = interactiveShellTool.startSession("bash", null);

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
        String sessionId = interactiveShellTool.startSession("bash", null);

        String result = interactiveShellTool.closeSession(sessionId);
        assertTrue(result.contains("closed"), "Close result should mention 'closed'");

        List<SessionInfo> sessions = interactiveShellTool.listSessions();
        assertTrue(sessions.stream().noneMatch(s -> s.sessionId().equals(sessionId)),
                "Closed session should not appear in list");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void listSessions_showsActiveSession() {
        String sessionId = interactiveShellTool.startSession("bash", null);

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
        String sessionId = interactiveShellTool.startSession("bash", null);

        interactiveShellTool.sendInput(sessionId, "export PTY_VAR=hello_from_pty");
        Thread.sleep(800);
        interactiveShellTool.sendInput(sessionId, "echo $PTY_VAR");
        Thread.sleep(800);

        SessionOutput output = interactiveShellTool.readSession(sessionId, 0);
        assertTrue(output.lines().stream().anyMatch(l -> l.contains("hello_from_pty")),
                "PTY session should preserve env vars between commands, got: " + output.lines());

        interactiveShellTool.closeSession(sessionId);
    }
}
