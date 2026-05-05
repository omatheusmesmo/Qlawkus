package dev.omatheusmesmo.qlawkus.tool.shell;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class OutputCapture extends Thread {

    private final int maxBytes;
    private final int maxLines;

    private static final byte[] TRUNCATION_SUFFIX = "\n[Output truncated]".getBytes(StandardCharsets.UTF_8);

    private final InputStream input;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean truncated = false;
    private IOException error;

    private int lineCount = 0;

    public OutputCapture(InputStream input, int maxBytes, int maxLines) {
        this.input = input;
        this.maxBytes = maxBytes;
        this.maxLines = maxLines;
        setDaemon(true);
    }

    @Override
    public void run() {
        byte[] chunk = new byte[8192];
        try {
            int read;
            while ((read = input.read(chunk)) != -1) {
                int allowed = maxBytes - buffer.size();
                if (allowed <= 0) {
                    truncated = true;
                    drainRemaining();
                    break;
                }
                if (read > allowed) {
                    buffer.write(chunk, 0, allowed);
                    truncated = true;
                    drainRemaining();
                    break;
                }
                buffer.write(chunk, 0, read);
                lineCount += countLines(chunk, read);
                if (lineCount > maxLines) {
                    truncated = true;
                    drainRemaining();
                    break;
                }
            }
            if (truncated) {
                int suffixSpace = maxBytes - buffer.size();
                if (suffixSpace > 0) {
                    buffer.write(TRUNCATION_SUFFIX, 0, Math.min(TRUNCATION_SUFFIX.length, suffixSpace));
                }
            }
        } catch (IOException e) {
            this.error = e;
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
            }
        }
    }

    public String getOutput() {
        String raw = buffer.toString(StandardCharsets.UTF_8);
        return stripExcessLines(raw);
    }

    public boolean isTruncated() {
        return truncated;
    }

    public IOException getError() {
        return error;
    }

    private String stripExcessLines(String raw) {
        if (maxLines <= 0) {
            return raw;
        }
        String[] lines = raw.split("\n", -1);
        if (lines.length <= maxLines) {
            return raw;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            sb.append(lines[i]);
            if (i < maxLines - 1) {
                sb.append("\n");
            }
        }
        sb.append("\n[").append(lines.length - maxLines).append(" more lines truncated]");
        return sb.toString();
    }

    private int countLines(byte[] chunk, int length) {
        int count = 0;
        for (int i = 0; i < length; i++) {
            if (chunk[i] == '\n') {
                count++;
            }
        }
        return count;
    }

    private void drainRemaining() {
        byte[] discard = new byte[8192];
        try {
            while (input.read(discard) != -1) {
            }
        } catch (IOException ignored) {
        }
    }
}
