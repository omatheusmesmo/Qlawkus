package dev.omatheusmesmo.qlawkus.messaging.logging;

import org.jboss.logmanager.MDC;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures the messaging INFO and WARN records a test produces, with the MDC values present when each
 * record was written. Messaging hops threads (Mutiny runs its stages on the worker pool), so records are
 * kept by logger name from any thread; tests make their ids unique so the lines they search for are theirs.
 */
public final class MessagingLogCapture implements AutoCloseable {

    private static final String PACKAGE = "dev.omatheusmesmo.qlawkus.messaging";

    public record Line(Level level, String message, Map<String, String> mdc) {
    }

    private final List<Line> lines = new CopyOnWriteArrayList<>();
    private final Logger root = Logger.getLogger("");
    private final Level previous;
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            String logger = record.getLoggerName();
            if (logger != null && logger.startsWith(PACKAGE) && record.getLevel().intValue() >= Level.INFO.intValue()) {
                lines.add(new Line(record.getLevel(), record.getMessage(), snapshot()));
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    public MessagingLogCapture() {
        previous = root.getLevel();
        handler.setLevel(Level.ALL);
        root.setLevel(Level.INFO);
        root.addHandler(handler);
    }

    public List<Line> lines() {
        return List.copyOf(lines);
    }

    public List<Line> containing(String fragment) {
        return lines.stream().filter(line -> line.message() != null && line.message().contains(fragment)).toList();
    }

    private static Map<String, String> snapshot() {
        return Map.of(
                "channel", String.valueOf(MDC.get("channel")),
                "conversation", String.valueOf(MDC.get("conversation")));
    }

    @Override
    public void close() {
        root.removeHandler(handler);
        root.setLevel(previous);
    }
}
