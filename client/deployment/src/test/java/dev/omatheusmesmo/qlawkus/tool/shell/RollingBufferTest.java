package dev.omatheusmesmo.qlawkus.tool.shell;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RollingBufferTest {

    @Test
    void addLine_andReadFromStart() {
        RollingBuffer buffer = new RollingBuffer(100);
        buffer.addLine("line1");
        buffer.addLine("line2");
        buffer.addLine("line3");

        List<String> lines = buffer.getLinesFrom(0);
        assertEquals(List.of("line1", "line2", "line3"), lines);
    }

    @Test
    void getLinesFrom_withOffset() {
        RollingBuffer buffer = new RollingBuffer(100);
        buffer.addLine("line0");
        buffer.addLine("line1");
        buffer.addLine("line2");

        List<String> lines = buffer.getLinesFrom(1);
        assertEquals(List.of("line1", "line2"), lines);
    }

    @Test
    void rollsOver_whenMaxLinesExceeded() {
        RollingBuffer buffer = new RollingBuffer(3);
        buffer.addLine("a");
        buffer.addLine("b");
        buffer.addLine("c");
        buffer.addLine("d");
        buffer.addLine("e");

        assertEquals(3, buffer.size());
        assertEquals(5, buffer.getTotalLinesAdded());

        List<String> lines = buffer.getLinesFrom(0);
        assertEquals(List.of("c", "d", "e"), lines);
    }

    @Test
    void getLinesFrom_offsetBeyondBuffer() {
        RollingBuffer buffer = new RollingBuffer(100);
        buffer.addLine("only");

        List<String> lines = buffer.getLinesFrom(10);
        assertTrue(lines.isEmpty(), "Offset beyond buffer should return empty");
    }

    @Test
    void getLinesFrom_offsetAccountForDiscarded() {
        RollingBuffer buffer = new RollingBuffer(2);
        buffer.addLine("a");
        buffer.addLine("b");
        buffer.addLine("c");

        List<String> lines = buffer.getLinesFrom(1);
        assertEquals(List.of("b", "c"), lines, "Offset 1 should skip 'a', but 'a' was discarded so start from 'b'");
    }

    @Test
    void hasMoreAfter() {
        RollingBuffer buffer = new RollingBuffer(100);
        buffer.addLine("a");
        buffer.addLine("b");

        assertTrue(buffer.hasMoreAfter(0));
        assertTrue(buffer.hasMoreAfter(1));
        assertFalse(buffer.hasMoreAfter(2));
    }

    @Test
    void clear_resetsState() {
        RollingBuffer buffer = new RollingBuffer(100);
        buffer.addLine("a");
        buffer.addLine("b");
        buffer.clear();

        assertEquals(0, buffer.size());
        assertEquals(0, buffer.getTotalLinesAdded());
        assertTrue(buffer.getLinesFrom(0).isEmpty());
    }
}
