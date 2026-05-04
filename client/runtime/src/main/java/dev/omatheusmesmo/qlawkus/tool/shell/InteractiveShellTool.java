package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.dto.SessionInfo;
import dev.omatheusmesmo.qlawkus.dto.SessionOutput;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import jakarta.inject.Inject;
import java.util.List;

@ClawTool
public class InteractiveShellTool {

    @Inject
    PtySessionManager sessionManager;

    @Tool("Start an interactive PTY session (e.g. bash, python REPL, docker exec). "
            + "Returns a sessionId for use with readSession/sendInput/closeSession. "
            + "Parameters: command (required) — the command to run in the PTY, "
            + "workdir (optional) — working directory (defaults to workspace root). "
            + "NOTE: PTY sessions are not available in native image mode — use runCommand instead.")
    public String startSession(String command, String workdir) {
        try {
            return sessionManager.startSession(command, workdir);
        } catch (UnsupportedOperationException e) {
            return "ERROR: " + e.getMessage();
        } catch (IllegalStateException e) {
            return "ERROR: " + e.getMessage();
        } catch (Exception e) {
            return "ERROR: Failed to start session: " + e.getMessage();
        }
    }

    @Tool("Read output from an interactive PTY session since a given line offset. " +
            "Parameters: sessionId (required) — the session ID from startSession, " +
            "offset (optional) — line offset to read from (default 0 for all buffered output)")
    public SessionOutput readSession(String sessionId, Integer offset) {
        try {
            return sessionManager.readSession(sessionId, offset != null ? offset : 0);
        } catch (IllegalArgumentException e) {
            return new SessionOutput(List.of("ERROR: " + e.getMessage()), false, 0);
        }
    }

    @Tool("Send input to an interactive PTY session (appended with newline by default). " +
            "Parameters: sessionId (required) — the session ID, " +
            "input (required) — the text to send to the PTY stdin")
    public String sendInput(String sessionId, String input) {
        try {
            sessionManager.sendInput(sessionId, input + "\n");
            return "Input sent";
        } catch (IllegalArgumentException e) {
            return "ERROR: " + e.getMessage();
        } catch (Exception e) {
            return "ERROR: Failed to send input: " + e.getMessage();
        }
    }

    @Tool("Close an interactive PTY session, terminating its process. " +
            "Parameter: sessionId (required) — the session ID to close")
    public String closeSession(String sessionId) {
        try {
            sessionManager.closeSession(sessionId);
            return "Session " + sessionId + " closed";
        } catch (IllegalArgumentException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool("List all active PTY sessions with their id, command, status, and last activity time")
    public List<SessionInfo> listSessions() {
        return sessionManager.listSessions();
    }
}
