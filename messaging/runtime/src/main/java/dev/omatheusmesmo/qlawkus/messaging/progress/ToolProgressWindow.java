package dev.omatheusmesmo.qlawkus.messaging.progress;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The lines of a status message: the most recent tool calls, with a count of the older ones that
 * scrolled off. Tool results never enter it.
 */
public final class ToolProgressWindow {

    private final int maxLines;
    private final List<Line> lines = new ArrayList<>();
    private int total;

    public ToolProgressWindow(int maxLines) {
        if (maxLines < 1) {
            throw new IllegalArgumentException("maxLines must be at least 1");
        }
        this.maxLines = maxLines;
    }

    public void started(String id, String name, String detail) {
        lines.add(new Line(id, name, detail, null, true));
        total++;
        while (lines.size() > maxLines) {
            lines.remove(0);
        }
    }

    /** Completes the line of a call that started earlier; a call that already scrolled off stays off. */
    public void ended(String id, String name, boolean ok, long durationMs) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            Line line = lines.get(i);
            if (line.id().equals(id)) {
                lines.set(i, new Line(id, line.name(), line.detail(), durationMs, ok));
                return;
            }
        }
    }

    public int total() {
        return total;
    }

    public String render() {
        StringBuilder text = new StringBuilder();
        int earlier = total - lines.size();
        if (earlier > 0) {
            text.append('+').append(earlier).append(" earlier").append('\n');
        }
        for (Line line : lines) {
            text.append(line.render()).append('\n');
        }
        return text.toString().stripTrailing();
    }

    static String seconds(long durationMs) {
        return String.format(Locale.ROOT, "%.1fs", durationMs / 1000.0);
    }

    private record Line(String id, String name, String detail, Long durationMs, boolean ok) {

        String render() {
            StringBuilder line = new StringBuilder(ToolEmoji.of(name)).append(' ').append(name);
            if (detail != null) {
                line.append(": ").append(detail);
            }
            if (durationMs != null) {
                line.append(" (").append(seconds(durationMs)).append(')');
            }
            if (!ok) {
                line.append(" failed");
            }
            return line.toString();
        }
    }
}
