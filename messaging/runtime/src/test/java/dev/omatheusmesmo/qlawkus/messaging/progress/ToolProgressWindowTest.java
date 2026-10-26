package dev.omatheusmesmo.qlawkus.messaging.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ToolProgressWindowTest {

    @Test
    void aCompletedCallShowsItsDetailAndDuration() {
        ToolProgressWindow window = new ToolProgressWindow(6);
        window.started("c1", "queryMetrics", "rate(qlawkus_tool_calls_total[1h])");
        window.ended("c1", "queryMetrics", true, 400);

        assertEquals("📊 queryMetrics: rate(qlawkus_tool_calls_total[1h]) (0.4s)", window.render());
    }

    @Test
    void aCallWithoutDetailShowsNameOnly() {
        ToolProgressWindow window = new ToolProgressWindow(6);
        window.started("c1", "webSearch", null);

        assertEquals("🔎 webSearch", window.render());
    }

    @Test
    void aFailedCallIsMarkedAsFailed() {
        ToolProgressWindow window = new ToolProgressWindow(6);
        window.started("c1", "shellExec", null);
        window.ended("c1", "shellExec", false, 1500);

        assertEquals("💻 shellExec (1.5s) failed", window.render());
    }

    @Test
    void olderLinesScrollOffAndTheCountRemains() {
        ToolProgressWindow window = new ToolProgressWindow(2);
        window.started("c1", "webSearch", null);
        window.started("c2", "webSearch", null);
        window.started("c3", "webSearch", null);

        assertEquals(3, window.total());
        assertEquals("+1 earlier\n🔎 webSearch\n🔎 webSearch", window.render());
    }

    @Test
    void endingACallThatAlreadyScrolledOffChangesNothing() {
        ToolProgressWindow window = new ToolProgressWindow(1);
        window.started("c1", "webSearch", null);
        window.started("c2", "readFile", null);
        window.ended("c1", "webSearch", true, 100);

        assertEquals("+1 earlier\n📄 readFile", window.render());
    }

    @Test
    void theWindowMustKeepAtLeastOneLine() {
        assertThrows(IllegalArgumentException.class, () -> new ToolProgressWindow(0));
    }
}
