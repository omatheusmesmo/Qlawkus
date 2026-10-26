package dev.omatheusmesmo.qlawkus.messaging.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.agent.ToolProgress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolProgressReporterTest {

    private final FakeSink sink = new FakeSink();
    private long now;
    private long pausedFor;

    @Test
    void theStatusMessageIsPostedAtTheFirstToolStart() {
        ToolProgressReporter reporter = reporter(ToolProgress.PREVIEW, sink);

        reporter.toolStarted("c1", "queryMetrics", "{\"q\":\"up\"}");

        assertEquals(List.of("post:chat-1:📊 queryMetrics: {\"q\":\"up\"}"), sink.log);
    }

    @Test
    void editsAreThrottledToOneEverySecondAndTheLatestStateWins() {
        ToolProgressReporter reporter = reporter(ToolProgress.STATUS, sink);

        reporter.toolStarted("c1", "webSearch", null);
        now += 200;
        reporter.toolEnded("c1", "webSearch", true, 200);
        now += 200;
        reporter.toolStarted("c2", "readFile", null);

        assertEquals(0, sink.edits(), "no edit inside the one-second window");

        now += 1000;
        reporter.toolEnded("c2", "readFile", true, 1400);

        assertEquals(1, sink.edits());
        assertTrue(sink.log.get(sink.log.size() - 1).contains("📄 readFile (1.4s)"));
    }

    @Test
    void completingWithSummaryEditsTheMessageToOneLine() {
        ToolProgressReporter reporter = reporter(ToolProgress.STATUS, sink);
        reporter.toolStarted("c1", "webSearch", null);
        reporter.toolEnded("c1", "webSearch", true, 10);
        reporter.toolStarted("c2", "webSearch", null);
        reporter.toolEnded("c2", "webSearch", true, 10);

        reporter.complete(ProgressCompletion.SUMMARY, 2, 4200);

        assertEquals("edit:chat-1:msg-1:2 tools, 4.2s", sink.log.get(sink.log.size() - 1));
    }

    @Test
    void completingWithDeleteRemovesTheMessage() {
        ToolProgressReporter reporter = reporter(ToolProgress.STATUS, sink);
        reporter.toolStarted("c1", "webSearch", null);

        reporter.complete(ProgressCompletion.DELETE, 1, 900);

        assertEquals("delete:chat-1:msg-1", sink.log.get(sink.log.size() - 1));
    }

    @Test
    void aTurnWithoutToolsTouchesNothing() {
        ToolProgressReporter reporter = reporter(ToolProgress.PREVIEW, sink);

        reporter.complete(ProgressCompletion.SUMMARY, 0, 1200);

        assertTrue(sink.log.isEmpty());
    }

    @Test
    void offPostsNothing() {
        ToolProgressReporter reporter = reporter(ToolProgress.OFF, sink);

        reporter.toolStarted("c1", "webSearch", null);
        reporter.complete(ProgressCompletion.SUMMARY, 1, 100);

        assertTrue(sink.log.isEmpty());
    }

    @Test
    void theFinalEditWaitsOutTheThrottleWindow() {
        ToolProgressReporter reporter = reporter(ToolProgress.STATUS, sink);
        reporter.toolStarted("c1", "webSearch", null);
        now += 300;

        reporter.complete(ProgressCompletion.SUMMARY, 1, 300);

        assertEquals(700L, pausedFor, "the edit waits the remaining 700 ms of the window");
    }

    @Test
    void aFailedPostStopsProgressForTheTurnWithoutThrowing() {
        sink.failPost = true;
        ToolProgressReporter reporter = reporter(ToolProgress.STATUS, sink);

        reporter.toolStarted("c1", "webSearch", null);
        reporter.toolStarted("c2", "webSearch", null);
        reporter.complete(ProgressCompletion.SUMMARY, 2, 100);

        assertEquals(1, sink.attempts, "no further platform calls after the first failure");
    }

    @Test
    void aFailedEditStopsProgressAndLaterCallsAreSkipped() {
        ToolProgressReporter reporter = reporter(ToolProgress.STATUS, sink);
        reporter.toolStarted("c1", "webSearch", null);
        sink.failEdit = true;
        now += 1000;
        reporter.toolEnded("c1", "webSearch", true, 1000);
        int attemptsAfterFailure = sink.attempts;

        reporter.complete(ProgressCompletion.SUMMARY, 1, 1000);

        assertEquals(attemptsAfterFailure, sink.attempts);
    }

    private ToolProgressReporter reporter(ToolProgress mode, FakeSink target) {
        return new ToolProgressReporter(mode, target, "chat-1", 6, () -> now, ms -> {
            pausedFor = ms;
            now += ms;
        });
    }

    private static final class FakeSink implements ToolProgressSink {

        final List<String> log = new ArrayList<>();
        int attempts;
        boolean failPost;
        boolean failEdit;

        @Override
        public String post(String chatId, String text) {
            attempts++;
            if (failPost) {
                throw new IllegalStateException("boom");
            }
            log.add("post:" + chatId + ":" + text);
            return "msg-1";
        }

        @Override
        public void edit(String chatId, String messageId, String text) {
            attempts++;
            if (failEdit) {
                throw new IllegalStateException("message to edit not found");
            }
            log.add("edit:" + chatId + ":" + messageId + ":" + text);
        }

        @Override
        public void delete(String chatId, String messageId) {
            attempts++;
            log.add("delete:" + chatId + ":" + messageId);
        }

        long edits() {
            return log.stream().filter(entry -> entry.startsWith("edit:")).count();
        }
    }
}
