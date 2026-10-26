package dev.omatheusmesmo.qlawkus.messaging.progress;

import dev.omatheusmesmo.qlawkus.agent.ToolProgress;
import io.quarkus.logging.Log;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Drives the one status message of a turn. The message is posted at the first tool start, never
 * before, and edited in place afterwards. Edits are throttled to one per {@link #MIN_EDIT_INTERVAL_MS}:
 * a change that arrives inside the window is not sent on its own, the next event or the completion
 * carries the latest state. The first failure stops progress for the turn, logged once, and the
 * reply is delivered as usual.
 */
public final class ToolProgressReporter {

    public static final long MIN_EDIT_INTERVAL_MS = 1_000L;

    private final ToolProgress mode;
    private final ToolProgressSink sink;
    private final String chatId;
    private final ToolProgressWindow window;
    private final LongSupplier clockMillis;
    private final LongConsumer pauseMillis;
    private String messageId;
    private String lastRendered;
    private long lastWriteAt;
    private boolean stopped;

    public ToolProgressReporter(ToolProgress mode, ToolProgressSink sink, String chatId, int maxLines,
                                LongSupplier clockMillis, LongConsumer pauseMillis) {
        this.mode = mode;
        this.sink = sink;
        this.chatId = chatId;
        this.window = new ToolProgressWindow(maxLines);
        this.clockMillis = clockMillis;
        this.pauseMillis = pauseMillis;
    }

    public static ToolProgressReporter forChat(ToolProgress mode, ToolProgressSink sink, String chatId,
                                               int maxLines) {
        return new ToolProgressReporter(mode, sink, chatId, maxLines, System::currentTimeMillis,
                ToolProgressReporter::sleep);
    }

    public synchronized void toolStarted(String id, String name, String arguments) {
        if (!mode.showsTools()) {
            return;
        }
        window.started(id, name, mode.detail(arguments));
        refresh();
    }

    public synchronized void toolEnded(String id, String name, boolean ok, long durationMs) {
        window.ended(id, name, ok, durationMs);
        refresh();
    }

    /**
     * Replaces the status message with its last state: a one-line summary, or nothing at all when
     * the turn ran no tool. Waits out the throttle window first so the final edit respects it too.
     */
    public synchronized void complete(ProgressCompletion completion, int toolCalls, long elapsedMs) {
        if (messageId == null || stopped) {
            return;
        }
        long wait = MIN_EDIT_INTERVAL_MS - (clockMillis.getAsLong() - lastWriteAt);
        if (wait > 0) {
            pauseMillis.accept(wait);
        }
        if (completion == ProgressCompletion.DELETE) {
            attempt(() -> sink.delete(chatId, messageId));
        } else {
            attempt(() -> sink.edit(chatId, messageId, summary(toolCalls, elapsedMs)));
        }
        stopped = true;
    }

    private void refresh() {
        if (stopped) {
            return;
        }
        if (messageId == null) {
            post(window.render());
            return;
        }
        if (clockMillis.getAsLong() - lastWriteAt >= MIN_EDIT_INTERVAL_MS) {
            edit(window.render());
        }
    }

    private void post(String text) {
        attempt(() -> {
            String id = sink.post(chatId, text);
            if (id == null) {
                throw new IllegalStateException("platform returned no message id");
            }
            messageId = id;
            lastWriteAt = clockMillis.getAsLong();
            lastRendered = text;
        });
    }

    private void edit(String text) {
        if (text.equals(lastRendered)) {
            return;
        }
        attempt(() -> {
            sink.edit(chatId, messageId, text);
            lastWriteAt = clockMillis.getAsLong();
            lastRendered = text;
        });
    }

    private void attempt(Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            stopped = true;
            Log.warnf("Tool progress stopped for chatId=%s: %s", chatId, e.getMessage());
        }
    }

    static String summary(int toolCalls, long elapsedMs) {
        return toolCalls + (toolCalls == 1 ? " tool, " : " tools, ") + ToolProgressWindow.seconds(elapsedMs);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
