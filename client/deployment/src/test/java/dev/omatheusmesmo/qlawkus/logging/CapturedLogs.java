package dev.omatheusmesmo.qlawkus.logging;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures the records the JBoss logging facade writes through JUL, so a test can pin what reaches
 * the INFO console and what stays at DEBUG. Only records written on the thread that opened the capture
 * are kept: other tests in the same JVM leave threads that keep logging at INFO.
 */
public final class CapturedLogs implements AutoCloseable {

    private final List<LogRecord> records = new CopyOnWriteArrayList<>();
    private final long ownerThread = Thread.currentThread().threadId();
    private final Logger root = Logger.getLogger("");
    private final Level previous;
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLongThreadID() == ownerThread) {
                records.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    public CapturedLogs() {
        previous = root.getLevel();
        handler.setLevel(Level.ALL);
        root.setLevel(Level.INFO);
        root.addHandler(handler);
    }

    /** Records at INFO or above, the lines a default console shows. */
    public List<LogRecord> info() {
        return records.stream().filter(r -> r.getLevel().intValue() >= Level.INFO.intValue()).toList();
    }

    @Override
    public void close() {
        root.removeHandler(handler);
        root.setLevel(previous);
    }
}
