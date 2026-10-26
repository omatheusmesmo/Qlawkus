package dev.omatheusmesmo.qlawkus.messaging.progress;

import java.util.HashMap;
import java.util.Map;

/**
 * The tool activity of one messaging turn. A tool is started when the model's response asks for it
 * and ended when the agent has executed it, so a line on the status message spans the real tool
 * run. The turn's reporter is null when the chat's mode shows no tools; the counts still apply.
 */
public final class ToolProgressTurn {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final ToolProgressReporter reporter;
    private final Map<String, Long> startedAtNanos = new HashMap<>();
    private final long startedAt = System.nanoTime();
    private int toolCalls;

    public ToolProgressTurn(ToolProgressReporter reporter) {
        this.reporter = reporter;
    }

    public void toolRequested(String id, String name, String arguments) {
        toolCalls++;
        startedAtNanos.put(id, System.nanoTime());
        if (reporter != null) {
            reporter.toolStarted(id, name, arguments);
        }
    }

    public void toolExecuted(String id, String name) {
        Long started = startedAtNanos.remove(id);
        long durationMs = started == null ? 0 : (System.nanoTime() - started) / NANOS_PER_MILLI;
        if (reporter != null) {
            reporter.toolEnded(id, name, true, durationMs);
        }
    }

    public void finish(ProgressCompletion completion) {
        if (reporter != null) {
            reporter.complete(completion, toolCalls, (System.nanoTime() - startedAt) / NANOS_PER_MILLI);
        }
    }
}
