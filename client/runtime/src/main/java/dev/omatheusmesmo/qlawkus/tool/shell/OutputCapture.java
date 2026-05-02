package dev.omatheusmesmo.qlawkus.tool.shell;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class OutputCapture extends Thread {

    private static final int MAX_BYTES = 1_048_576;
    private static final byte[] TRUNCATION_SUFFIX = "\n[Output truncated — exceeded 1MB limit]".getBytes(StandardCharsets.UTF_8);

    private final InputStream input;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean truncated = false;
    private IOException error;

    OutputCapture(InputStream input) {
        this.input = input;
        setDaemon(true);
    }

    @Override
    public void run() {
        byte[] chunk = new byte[8192];
        try {
            int read;
            while ((read = input.read(chunk)) != -1) {
                int allowed = MAX_BYTES - buffer.size();
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
            }
            if (truncated) {
                int suffixSpace = MAX_BYTES - buffer.size();
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

    String getOutput() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    boolean isTruncated() {
        return truncated;
    }

    IOException getError() {
        return error;
    }

    private void drainRemaining() {
        byte[] discard = new byte[8192];
        try {
            while (input.read(discard) != -1) {
                // drain to unblock the writing process
            }
        } catch (IOException ignored) {
        }
    }
}
