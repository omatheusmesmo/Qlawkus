package dev.omatheusmesmo.qlawkus.messaging;

import io.quarkus.logging.Log;
import org.jboss.logmanager.MDC;

import java.util.function.Supplier;

/**
 * Writes a messaging log line with the {@code channel} and {@code conversation} MDC keys present for that
 * line only. A Mutiny chain hops threads between its stages, so a thread-bound MDC set once at the top of an
 * inbound message would be missing on the stage that delivers the reply; setting it per line keeps the keys
 * on the line and never leaks them onto a pooled thread.
 */
public final class MessagingLog {

    private static final String CHANNEL = "channel";
    private static final String CONVERSATION = "conversation";

    private MessagingLog() {
    }

    public static void info(String channel, String conversation, String format, Object... args) {
        logWithin(channel, conversation, () -> Log.infof(format, args));
    }

    public static void warn(String channel, String conversation, String format, Object... args) {
        logWithin(channel, conversation, () -> Log.warnf(format, args));
    }

    public static <T> T within(String channel, String conversation, Supplier<T> action) {
        String previousChannel = MDC.get(CHANNEL);
        String previousConversation = MDC.get(CONVERSATION);
        if (channel != null) {
            MDC.put(CHANNEL, channel);
        }
        if (conversation != null) {
            MDC.put(CONVERSATION, conversation);
        }
        try {
            return action.get();
        } finally {
            restore(CHANNEL, previousChannel);
            restore(CONVERSATION, previousConversation);
        }
    }

    public static String exceptionName(Throwable failure) {
        return failure.getClass().getSimpleName();
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previous);
        }
    }

    private static void logWithin(String channel, String conversation, Runnable line) {
        within(channel, conversation, () -> {
            line.run();
            return null;
        });
    }
}
