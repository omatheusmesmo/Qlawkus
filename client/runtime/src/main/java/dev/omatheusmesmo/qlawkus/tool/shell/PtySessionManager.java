package dev.omatheusmesmo.qlawkus.tool.shell;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import dev.omatheusmesmo.qlawkus.config.ShellConfig;
import dev.omatheusmesmo.qlawkus.dto.SessionInfo;
import dev.omatheusmesmo.qlawkus.dto.SessionOutput;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import io.quarkus.logging.Log;
import io.quarkus.runtime.ImageMode;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
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

    @Inject
    AgentMeters meters;


    @Inject
    public ShellConfig shellConfig;

    @Inject
    WorkspaceConfinement workspaceConfinement;

    @Inject
    ChildEnvironment childEnvironment;

    @Inject
    SecretRedactor secretRedactor;

    /**
     * Maximum number of concurrent PTY sessions. Default: 10.
     */
    public int maxSessions;

    public int idleTimeoutMinutes;

    /**
     * Maximum lines retained in session output buffer. Default: 50000.
     */
    int bufferLines;

    /**
     * Default terminal columns for new sessions. Default: 120.
     */
    int defaultCols;

    /**
     * Default terminal rows for new sessions. Default: 40.
     */
    int defaultRows;

    /**
     * Workspace root directory for PTY session execution. Default: {@code .}.
     */
    String workspaceRoot;

    /**
     * Default shell command. Use "auto" to detect from $SHELL. Default: {@code auto}.
     */
    String defaultShellConfig;

    /**
     * Whether to append --norc --noprofile to bare shell names. Default: {@code true}.
     */
    public boolean cleanProfile;

    /**
     * Regex patterns for shell prompt detection. Optional — defaults to empty list.
     */
    List<String> defaultPromptPatterns;
    private String detectedDefaultShell;

    private final ConcurrentHashMap<String, PtySession> sessions = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        this.workspaceRoot = workspaceConfinement.getWorkspacePath().toString();
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
        if (this.maxSessions <= 0) this.maxSessions = 10;
        if (this.idleTimeoutMinutes <= 0) this.idleTimeoutMinutes = 30;
        if (this.bufferLines <= 0) this.bufferLines = 50000;
        if (this.defaultCols <= 0) this.defaultCols = 120;
        if (this.defaultRows <= 0) this.defaultRows = 40;
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

    public synchronized String startSession(String command, String workdir, List<String> prompts) throws IOException {
        if (sessions.size() >= maxSessions) {
            throw new IllegalStateException("PTY session limit reached (" + maxSessions + "). Close a session first.");
        }

        String sessionId = UUID.randomUUID().toString();
        String effectiveCommand = resolveCommand(command);
        File dir = resolveDir(workdir);

        Map<String, String> env = new HashMap<>(childEnvironment.build());
        env.put("TERM", "xterm-256color");

        RollingBuffer buffer = new RollingBuffer(bufferLines);
        List<Pattern> compiledPrompts = compilePromptPatterns(prompts);
        PtyConnection terminal = ImageMode.current() == ImageMode.NATIVE_RUN
                ? LinuxScriptPty.start(effectiveCommand, dir, env, defaultCols, defaultRows)
                : startPty4j(effectiveCommand, dir, env);
        PtySession session = new PtySession(sessionId, effectiveCommand, terminal, buffer, null, compiledPrompts);

        Thread readerThread = new Thread(() -> {
            try (BufferedReader reader = terminal.output()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.addLine(line);
                    session.checkPrompt(line);
                }
            } catch (IOException e) {
                if (sessions.get(sessionId) == session) {
                    Log.debugf("PTY reader error for session %s: %s", sessionId, e.getMessage());
                }
            }
        }, "pty-reader-" + sessionId);
        readerThread.setDaemon(true);
        session.setReaderThread(readerThread);
        sessions.put(sessionId, session);
        readerThread.start();

        Log.infof("PTY session started: id=%s command='%s' dir=%s", sessionId, secretRedactor.redact(command), dir);
        return sessionId;
    }

    private PtyConnection startPty4j(String command, File directory, Map<String, String> environment) throws IOException {
        String[] arguments = ShellTool.IS_WINDOWS
                ? new String[]{"cmd", "/c", command}
                : new String[]{"sh", "-c", command};
        PtyProcess process = new PtyProcessBuilder()
                .setCommand(arguments)
                .setEnvironment(environment)
                .setDirectory(directory.getAbsolutePath())
                .setInitialColumns(defaultCols)
                .setInitialRows(defaultRows)
                .start();
        return new PtyConnection(process, process.inputReader(StandardCharsets.UTF_8),
                (columns, rows) -> process.setWinSize(new WinSize(columns, rows)), process::destroyForcibly);
    }

    public SessionOutput readSession(String sessionId, int offset) {
        PtySession session = requireSession(sessionId);
        session.updateStatus();
        session.touchActivity();

        List<String> lines = session.getOutputBuffer().getLinesFrom(offset).stream()
                .map(secretRedactor::redact)
                .toList();
        boolean hasMore = session.getOutputBuffer().hasMoreAfter(offset + lines.size());
        boolean promptDetected = session.isPromptDetected();
        session.clearPromptDetected();

        return new SessionOutput(lines, hasMore, offset + lines.size(), promptDetected);
    }

    public void sendInput(String sessionId, String input) throws IOException {
        PtySession session = requireSession(sessionId);
        session.sendInput(input);
    }

    public void resizeSession(String sessionId, int columns, int rows) throws IOException {
        if (columns < 1 || rows < 1 || columns > 65_535 || rows > 65_535) {
            throw new IllegalArgumentException("Terminal dimensions must be between 1 and 65535.");
        }
        requireSession(sessionId).setWinSize(columns, rows);
    }

    public void closeSession(String sessionId) {
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
                    secretRedactor.redact(session.getCommand()),
                    session.getStatus(),
                    session.getLastActivity().toEpochMilli(),
                    session.getPromptPatternStrings()
            ));
        }
        return result;
    }

    @Scheduled(identity = "pty-session-sweep", every = "60s")
    void cleanupIdleSessions() {
        meters.timeJob("pty-session-sweep", this::sweepIdleSessions);
    }

    /**
     * Closes sessions that exited or went idle past the timeout, returning how many were reaped so
     * the sweep reports items processed like every other scheduled job.
     */
    long sweepIdleSessions() {
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
                    session.close();
                    Log.infof("PTY session cleanup (exited): id=%s", id);
                } else {
                    session.markTimedOut();
                    Log.infof("PTY session cleanup (idle timeout %dm): id=%s", idleTimeoutMinutes, id);
                }
            }
        }
        return toClose.size();
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

    public List<Pattern> compilePromptPatterns(List<String> prompts) {
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
