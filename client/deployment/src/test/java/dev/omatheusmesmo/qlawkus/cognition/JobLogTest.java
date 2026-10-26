package dev.omatheusmesmo.qlawkus.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.logging.CapturedLogs;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

class JobLogTest {

    @Test
    void aJobLogsItsStartAndItsEndWithItsCounts() {
        try (CapturedLogs logs = new CapturedLogs()) {
            JobLog.Run run = JobLog.start("memory-review");
            run.count("scanned", 12).count("removed", 3);
            run.finish();

            List<LogRecord> info = logs.info();
            assertEquals(2, info.size(), "start and end: " + info);
            assertEquals("Job memory-review started", info.get(0).getMessage());
            String end = info.get(1).getMessage();
            assertTrue(end.startsWith("Job memory-review finished"), end);
            assertTrue(end.contains("scanned=12 removed=3"), end);
            assertTrue(end.contains("duration="), end);
        }
    }

    @Test
    void aFailedJobLogsAWarningNamingTheExceptionClassOnly() {
        try (CapturedLogs logs = new CapturedLogs()) {
            JobLog.Run run = JobLog.start("skill-curation");
            run.fail(new IllegalStateException("store unavailable for alice@example.com"));

            LogRecord failure = logs.info().get(logs.info().size() - 1);
            assertEquals(Level.WARNING, failure.getLevel());
            assertTrue(failure.getMessage().contains("Job skill-curation failed"), failure.getMessage());
            assertTrue(failure.getMessage().contains("IllegalStateException"), failure.getMessage());
            assertFalse(failure.getMessage().contains("alice"), failure.getMessage());
        }
    }
}
