package dev.omatheusmesmo.qlawkus.tool.shell;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PtySession {

    private final String sessionId;
    private final String command;
    private final PtyConnection terminal;
    private final RollingBuffer outputBuffer;
    private Thread readerThread;
    private final Instant startedAt;
    private final List<Pattern> promptPatterns;
    private volatile Instant lastActivity;
    private volatile String status;
    private volatile boolean promptDetected;

    PtySession(String sessionId, String command, PtyConnection terminal, RollingBuffer outputBuffer,
               Thread readerThread, List<Pattern> promptPatterns) {
        this.sessionId = sessionId;
        this.command = command;
        this.terminal = terminal;
        this.outputBuffer = outputBuffer;
        this.readerThread = readerThread;
        this.promptPatterns = promptPatterns != null ? promptPatterns : List.of();
        this.startedAt = Instant.now();
        this.lastActivity = Instant.now();
        this.status = "running";
        this.promptDetected = false;
    }

    void setReaderThread(Thread thread) {
        this.readerThread = thread;
    }

    void checkPrompt(String line) {
        if (promptPatterns.isEmpty()) {
            return;
        }
        for (Pattern pattern : promptPatterns) {
            if (pattern.matcher(line).find()) {
                promptDetected = true;
                return;
            }
        }
    }

    public boolean isPromptDetected() {
        return promptDetected;
    }

    public void clearPromptDetected() {
        promptDetected = false;
    }

    public List<String> getPromptPatternStrings() {
        List<String> result = new ArrayList<>();
        for (Pattern p : promptPatterns) {
            result.add(p.pattern());
        }
        return result;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCommand() {
        return command;
    }

    public String getStatus() {
        return status;
    }

    public Instant getLastActivity() {
        return lastActivity;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public RollingBuffer getOutputBuffer() {
        return outputBuffer;
    }

    public void sendInput(String input) throws IOException {
        OutputStream os = terminal.process().getOutputStream();
        os.write(input.getBytes(StandardCharsets.UTF_8));
        os.flush();
        lastActivity = Instant.now();
    }

    public synchronized void setWinSize(int cols, int rows) throws IOException {
        if (!isAlive()) {
            throw new IOException("The terminal has exited.");
        }
        terminal.resize().apply(cols, rows);
        lastActivity = Instant.now();
    }

    public boolean isAlive() {
        return terminal != null && terminal.process().isAlive();
    }

    public synchronized void close() {
        stop("closed");
    }

    synchronized void markTimedOut() {
        stop("timed_out");
    }

    private void stop(String outcome) {
        status = outcome;
        if (terminal != null) {
            terminal.stop().run();
            try {
                terminal.process().getOutputStream().close();
            } catch (IOException ignored) {
                // The other end may already have closed.
            }
        }
        if (readerThread != null) readerThread.interrupt();
    }

    void updateStatus() {
        if ("running".equals(status) && terminal != null && !isAlive()) {
            status = "exited";
        }
    }

    void touchActivity() {
        lastActivity = Instant.now();
    }
}
