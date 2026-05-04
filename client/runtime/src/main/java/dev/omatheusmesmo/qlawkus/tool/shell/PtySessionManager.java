package dev.omatheusmesmo.qlawkus.tool.shell;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import dev.omatheusmesmo.qlawkus.dto.SessionInfo;
import dev.omatheusmesmo.qlawkus.dto.SessionOutput;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class PtySessionManager {

    static final String NATIVE_IMAGE_UNAVAILABLE = "PTY sessions are not available in native image mode (JNA/pty4j requires JVM). Use ShellTool.runCommand instead.";

    private final boolean nativeImageMode;

    @ConfigProperty(name = "qlawkus.shell.pty.max-sessions", defaultValue = "10")
    int maxSessions;

    @ConfigProperty(name = "qlawkus.shell.pty.idle-timeout-minutes", defaultValue = "30")
    int idleTimeoutMinutes;

    @ConfigProperty(name = "qlawkus.shell.pty.buffer-lines", defaultValue = "50000")
    int bufferLines;

    @ConfigProperty(name = "qlawkus.shell.pty.default-cols", defaultValue = "120")
    int defaultCols;

    @ConfigProperty(name = "qlawkus.shell.pty.default-rows", defaultValue = "40")
    int defaultRows;

    @ConfigProperty(name = "qlawkus.shell.workspace-root", defaultValue = ".")
    String workspaceRoot;

    private final ConcurrentHashMap<String, PtySession> sessions = new ConcurrentHashMap<>();

    @Inject
    WorkspaceConfinement workspaceConfinement;

    PtySessionManager() {
        nativeImageMode = "true".equals(System.getProperty("io.quarkus.native.image", "false"))
                || "native".equals(System.getProperty("quarkus.native.image.type"))
                || isSubstrateVM();
        if (nativeImageMode) {
            Log.infof("PTY sessions disabled: running in native image mode (JNA/pty4j not supported)");
        }
    }

    static boolean isSubstrateVM() {
        try {
            Class.forName("com.oracle.svm.core.VM");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public boolean isNativeImageMode() {
        return nativeImageMode;
    }

    public String startSession(String command, String workdir) throws IOException {
        if (nativeImageMode) {
            throw new UnsupportedOperationException(NATIVE_IMAGE_UNAVAILABLE);
        }
        if (sessions.size() >= maxSessions) {
            throw new IllegalStateException("PTY session limit reached (" + maxSessions + "). Close a session first.");
        }

        String sessionId = UUID.randomUUID().toString();
        String[] cmdArgs = ShellTool.IS_WINDOWS
                ? new String[]{"cmd", "/c", command}
                : new String[]{"sh", "-c", command};

        java.io.File dir = resolveDir(workdir);

        Map<String, String> env = new java.util.HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");

        PtyProcess process = new PtyProcessBuilder()
                .setCommand(cmdArgs)
                .setEnvironment(env)
                .setDirectory(dir.getAbsolutePath())
                .setInitialColumns(defaultCols)
                .setInitialRows(defaultRows)
                .start();

        RollingBuffer buffer = new RollingBuffer(bufferLines);

        Thread readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.addLine(line);
                }
            } catch (IOException e) {
                if (!"closed".equals(sessions.get(sessionId) != null ? sessions.get(sessionId).getStatus() : "")) {
                    Log.debugf("PTY reader error for session %s: %s", sessionId, e.getMessage());
                }
            }
        }, "pty-reader-" + sessionId);
        readerThread.setDaemon(true);
        readerThread.start();

        PtySession session = new PtySession(sessionId, command, process, buffer, readerThread);
        sessions.put(sessionId, session);

        Log.infof("PTY session started: id=%s command='%s' dir=%s", sessionId, command, dir);
        return sessionId;
    }

    public SessionOutput readSession(String sessionId, int offset) {
        if (nativeImageMode) {
            return new SessionOutput(List.of("ERROR: " + NATIVE_IMAGE_UNAVAILABLE), false, 0);
        }
        PtySession session = requireSession(sessionId);
        session.updateStatus();
        session.touchActivity();

        List<String> lines = session.getOutputBuffer().getLinesFrom(offset);
        boolean hasMore = session.getOutputBuffer().hasMoreAfter(offset + lines.size());

        return new SessionOutput(lines, hasMore, offset + lines.size());
    }

    public void sendInput(String sessionId, String input) throws IOException {
        if (nativeImageMode) {
            throw new UnsupportedOperationException(NATIVE_IMAGE_UNAVAILABLE);
        }
        PtySession session = requireSession(sessionId);
        session.sendInput(input);
    }

    public void closeSession(String sessionId) {
        if (nativeImageMode) {
            throw new UnsupportedOperationException(NATIVE_IMAGE_UNAVAILABLE);
        }
        PtySession session = sessions.remove(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("No PTY session with id: " + sessionId);
        }
        session.close();
        Log.infof("PTY session closed: id=%s", sessionId);
    }

    public List<SessionInfo> listSessions() {
        List<SessionInfo> result = new ArrayList<>();
        for (PtySession session : sessions.values()) {
            session.updateStatus();
            result.add(new SessionInfo(
                    session.getSessionId(),
                    session.getCommand(),
                    session.getStatus(),
                    session.getLastActivity().toEpochMilli()
            ));
        }
        return result;
    }

    @Scheduled(every = "60s")
    void cleanupIdleSessions() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(idleTimeoutMinutes));
        List<String> toClose = new ArrayList<>();

        for (Map.Entry<String, PtySession> entry : sessions.entrySet()) {
            PtySession session = entry.getValue();
            session.updateStatus();
            if (session.getLastActivity().isBefore(threshold) || "exited".equals(session.getStatus())) {
                toClose.add(entry.getKey());
            }
        }

        for (String id : toClose) {
            PtySession session = sessions.remove(id);
            if (session != null) {
                if ("exited".equals(session.getStatus())) {
                    Log.infof("PTY session cleanup (exited): id=%s", id);
                } else {
                    session.markTimedOut();
                    Log.infof("PTY session cleanup (idle timeout %dm): id=%s", idleTimeoutMinutes, id);
                }
            }
        }
    }

    @PreDestroy
    void shutdownAll() {
        for (Map.Entry<String, PtySession> entry : sessions.entrySet()) {
            entry.getValue().close();
            Log.infof("PTY session closed on shutdown: id=%s", entry.getKey());
        }
        sessions.clear();
    }

    private PtySession requireSession(String sessionId) {
        PtySession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("No PTY session with id: " + sessionId);
        }
        return session;
    }

    private java.io.File resolveDir(String workdir) {
        if (workdir == null || workdir.isBlank()) {
            return new java.io.File(workspaceRoot).getAbsoluteFile();
        }
        var security = workspaceConfinement.check(workdir);
        if (security.blocked()) {
            throw new SecurityException("Workdir blocked: " + security.reason());
        }
        return workspaceConfinement.resolveCanonical(workdir).toFile();
    }
}
