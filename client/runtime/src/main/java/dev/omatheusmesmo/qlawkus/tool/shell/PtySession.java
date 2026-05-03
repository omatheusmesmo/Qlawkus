package dev.omatheusmesmo.qlawkus.tool.shell;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class PtySession {

    private final String sessionId;
    private final String command;
    private final PtyProcess process;
    private final RollingBuffer outputBuffer;
    private final Thread readerThread;
    private final Instant startedAt;
    private volatile Instant lastActivity;
    private volatile String status;

    PtySession(String sessionId, String command, PtyProcess process, RollingBuffer outputBuffer, Thread readerThread) {
        this.sessionId = sessionId;
        this.command = command;
        this.process = process;
        this.outputBuffer = outputBuffer;
        this.readerThread = readerThread;
        this.startedAt = Instant.now();
        this.lastActivity = Instant.now();
        this.status = "running";
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
        OutputStream os = process.getOutputStream();
        os.write(input.getBytes(StandardCharsets.UTF_8));
        os.flush();
        lastActivity = Instant.now();
    }

    public void setWinSize(int cols, int rows) {
        process.setWinSize(new WinSize(cols, rows));
        lastActivity = Instant.now();
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    public void close() {
        status = "closed";
        process.destroyForcibly();
        readerThread.interrupt();
    }

    void markTimedOut() {
        status = "timed_out";
        process.destroyForcibly();
        readerThread.interrupt();
    }

    void updateStatus() {
        if ("running".equals(status) && !process.isAlive()) {
            status = "exited";
        }
    }

    void touchActivity() {
        lastActivity = Instant.now();
    }
}
