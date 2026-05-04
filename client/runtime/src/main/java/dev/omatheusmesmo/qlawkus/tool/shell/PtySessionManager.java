package dev.omatheusmesmo.qlawkus.tool.shell;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import dev.omatheusmesmo.qlawkus.config.ShellConfig;
import dev.omatheusmesmo.qlawkus.dto.SessionInfo;
import dev.omatheusmesmo.qlawkus.dto.SessionOutput;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@ApplicationScoped
public class PtySessionManager {

    static final String NATIVE_IMAGE_UNAVAILABLE = "PTY sessions are not available in native image mode (JNA/pty4j requires JVM). Use ShellTool.runCommand instead.";

    private final boolean nativeImageMode;

    @Inject
    ShellConfig shellConfig;

    @Inject
    WorkspaceConfinement workspaceConfinement;

    int maxSessions;
    int idleTimeoutMinutes;
    int bufferLines;
    int defaultCols;
    int defaultRows;
    String workspaceRoot;
    String defaultShellConfig;
    public boolean cleanProfile;
    List<String> defaultPromptPatterns;
    private String detectedDefaultShell;

    private final ConcurrentHashMap<String, PtySession> sessions = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        this.workspaceRoot = shellConfig.workspaceRoot();
        this.defaultShellConfig = shellConfig.defaultShell();
        this.cleanProfile = shellConfig.cleanProfile();
        this.defaultPromptPatterns = shellConfig.prompts().orElse(List.of());
        ShellConfig.PtyConfig pty = shellConfig.pty();
        if (pty != null) {
            this.maxSessions = pty.maxSessions();
            this.idleTimeoutMinutes = pty.idleTimeoutMinutes();
            this.bufferLines = pty.bufferLines();
            this.defaultCols = pty.defaultCols();
            this.defaultRows = pty.defaultRows();
        }
        detectedDefaultShell = detectDefaultShell();
        if (!"auto".equals(defaultShellConfig)) {
            detectedDefaultShell = defaultShellConfig;
        }
        Log.infof("Default shell: %s, clean-profile: %s", detectedDefaultShell, cleanProfile);
    }

    static String detectDefaultShell() {
        if (ShellTool.IS_WINDOWS) {
            return "cmd.exe";
        }
        String shell = System.getenv("SHELL");
        if (shell != null && !shell.isBlank()) {
            return shell;
        }
        return "/bin/sh";
    }

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

    public String startSession(String command, String workdir, List<String> prompts) throws IOException {
        if (nativeImageMode) {
            throw new UnsupportedOperationException(NATIVE_IMAGE_UNAVAILABLE);
        }
        if (sessions.size() >= maxSessions) {
            throw new IllegalStateException("PTY session limit reached (" + maxSessions + "). Close a session first.");
        }

        String sessionId = UUID.randomUUID().toString();
        String effectiveCommand = resolveCommand(command);
        String[] cmdArgs = ShellTool.IS_WINDOWS
                ? new String[]{"cmd", "/c", effectiveCommand}
                : new String[]{"sh", "-c", effectiveCommand};

        File dir = resolveDir(workdir);

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");

        PtyProcess process = new PtyProcessBuilder()
                .setCommand(cmdArgs)
                .setEnvironment(env)
                .setDirectory(dir.getAbsolutePath())
                .setInitialColumns(defaultCols)
                .setInitialRows(defaultRows)
                .start();

        RollingBuffer buffer = new RollingBuffer(bufferLines);
        List<Pattern> compiledPrompts = compilePromptPatterns(prompts);
        PtySession session = new PtySession(sessionId, effectiveCommand, process, buffer, null, compiledPrompts);

        Thread readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.addLine(line);
                    session.checkPrompt(line);
                }
            } catch (IOException e) {
                if (!"closed".equals(sessions.get(sessionId) != null ? sessions.get(sessionId).getStatus() : "")) {
                    Log.debugf("PTY reader error for session %s: %s", sessionId, e.getMessage());
                }
            }
        }, "pty-reader-" + sessionId);
        readerThread.setDaemon(true);
        readerThread.start();

        session.setReaderThread(readerThread);
        sessions.put(sessionId, session);

        Log.infof("PTY session started: id=%s command='%s' dir=%s", sessionId, command, dir);
        return sessionId;
    }

    public SessionOutput readSession(String sessionId, int offset) {
        if (nativeImageMode) {
            return new SessionOutput(List.of("ERROR: " + NATIVE_IMAGE_UNAVAILABLE), false, 0, false);
        }
        PtySession session = requireSession(sessionId);
        session.updateStatus();
        session.touchActivity();

        List<String> lines = session.getOutputBuffer().getLinesFrom(offset);
        boolean hasMore = session.getOutputBuffer().hasMoreAfter(offset + lines.size());
        boolean promptDetected = session.isPromptDetected();
        session.clearPromptDetected();

        return new SessionOutput(lines, hasMore, offset + lines.size(), promptDetected);
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
                    session.getLastActivity().toEpochMilli(),
                    session.getPromptPatternStrings()
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

    private File resolveDir(String workdir) {
        if (workdir == null || workdir.isBlank()) {
            return new File(workspaceRoot).getAbsoluteFile();
        }
        var security = workspaceConfinement.check(workdir);
        if (security.blocked()) {
            throw new SecurityException("Workdir blocked: " + security.reason());
        }
        return workspaceConfinement.resolveCanonical(workdir).toFile();
    }

    public String resolveCommand(String command) {
        if (command == null || command.isBlank()) {
            return applyCleanProfile(detectedDefaultShell);
        }
        String trimmed = command.trim();
        if (isBareShellName(trimmed)) {
            return applyCleanProfile(resolveShellPath(trimmed));
        }
        return command;
    }

    private boolean isBareShellName(String command) {
        return !command.contains("/") && !command.contains("\\") && !command.contains(" ")
                && List.of("bash", "sh", "zsh", "fish", "dash", "ksh", "csh", "tcsh").contains(command);
    }

    private String resolveShellPath(String shellName) {
        if (ShellTool.IS_WINDOWS) {
            return shellName;
        }
        return "/bin/" + shellName;
    }

    private String applyCleanProfile(String shellCommand) {
        if (!cleanProfile || ShellTool.IS_WINDOWS) {
            return shellCommand;
        }
        String lower = shellCommand.toLowerCase();
        if (lower.endsWith("/bash") || lower.endsWith("/sh") || lower.endsWith("/zsh")
                || lower.endsWith("/dash") || lower.endsWith("/ksh")) {
            return shellCommand + " --norc --noprofile";
        }
        return shellCommand;
    }

    List<Pattern> compilePromptPatterns(List<String> prompts) {
        List<String> effective = (prompts != null && !prompts.isEmpty()) ? prompts : defaultPromptPatterns;
        if (effective == null || effective.isEmpty()) {
            return List.of();
        }
        List<Pattern> compiled = new ArrayList<>();
        for (String p : effective) {
            if (p != null && !p.isBlank()) {
                try {
                    compiled.add(Pattern.compile(p));
                } catch (PatternSyntaxException e) {
                    Log.warnf("Invalid prompt pattern '%s': %s", p, e.getMessage());
                }
            }
        }
        return compiled;
    }

    public String getDefaultShell() {
        return detectedDefaultShell;
    }
}
