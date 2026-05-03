package dev.omatheusmesmo.qlawkus.tool.shell;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class RollingBuffer {

    private final Deque<String> lines = new ConcurrentLinkedDeque<>();
    private final int maxLines;
    private final AtomicInteger totalLinesAdded = new AtomicInteger(0);

    public RollingBuffer(int maxLines) {
        this.maxLines = maxLines;
    }

    public void addLine(String line) {
        lines.addLast(line);
        totalLinesAdded.incrementAndGet();
        while (lines.size() > maxLines) {
            lines.removeFirst();
        }
    }

    public List<String> getLinesFrom(int offset) {
        int currentSize = lines.size();
        if (offset < 0 || offset >= totalLinesAdded.get()) {
            return List.of();
        }
        int discarded = totalLinesAdded.get() - currentSize;
        int adjustedOffset = offset - discarded;
        if (adjustedOffset < 0) {
            adjustedOffset = 0;
        }
        List<String> result = new ArrayList<>();
        int idx = 0;
        for (String line : lines) {
            if (idx >= adjustedOffset) {
                result.add(line);
            }
            idx++;
            if (result.size() >= maxLines) {
                break;
            }
        }
        return result;
    }

    public boolean hasMoreAfter(int offset) {
        int currentTotal = totalLinesAdded.get();
        return offset < currentTotal;
    }

    public int getTotalLinesAdded() {
        return totalLinesAdded.get();
    }

    public int size() {
        return lines.size();
    }

    public void clear() {
        lines.clear();
        totalLinesAdded.set(0);
    }
}
