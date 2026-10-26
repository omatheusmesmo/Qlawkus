package dev.omatheusmesmo.qlawkus.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.omatheusmesmo.qlawkus.logging.CapturedLogs;
import java.util.List;
import java.util.logging.LogRecord;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.Test;

class MeteredToolExecutorLogTest {

    private static final String ARGUMENTS = "{\"query\":\"my dentist appointment\"}";
    private static final String RESULT = "SECRET-RESULT-BODY";

    @Test
    void oneInfoLinePerToolCallNamesTheToolAndItsOutcomeButNotTheArgumentsOrResult() {
        ToolExecutor delegate = (request, memoryId) -> RESULT;
        MeteredToolExecutor executor = new MeteredToolExecutor("searchMemories", delegate, AgentMeters.disabled());

        try (CapturedLogs logs = new CapturedLogs()) {
            executor.execute(request(), "qlawkus-rest");

            List<LogRecord> info = logs.info();
            assertEquals(1, info.size(), "one line per tool call: " + info);
            String line = info.get(0).getMessage();
            assertTrue(line.contains("searchMemories"), line);
            assertTrue(line.contains("outcome=ok"), line);
            assertTrue(line.contains("duration="), line);
            assertFalse(line.contains("dentist"), line);
            assertFalse(line.contains("SECRET"), line);
        }
    }

    @Test
    void aFailingToolLogsItsOutcomeAsError() {
        ToolExecutor delegate = (request, memoryId) -> {
            throw new IllegalStateException("boom");
        };
        MeteredToolExecutor executor = new MeteredToolExecutor("deleteSkill", delegate, AgentMeters.disabled());

        try (CapturedLogs logs = new CapturedLogs()) {
            assertThrows(executor);

            String line = logs.info().get(0).getMessage();
            assertTrue(line.contains("deleteSkill") && line.contains("outcome=error"), line);
        }
    }

    @Test
    void theToolNameIsInTheMdcOnlyWhileTheCallRuns() {
        ToolExecutor delegate = (request, memoryId) -> {
            assertEquals("searchMemories", MDC.get("tool"));
            return RESULT;
        };
        MeteredToolExecutor executor = new MeteredToolExecutor("searchMemories", delegate, AgentMeters.disabled());

        try (CapturedLogs ignored = new CapturedLogs()) {
            executor.execute(request(), "qlawkus-rest");
        }

        assertNull(MDC.get("tool"));
    }

    private static void assertThrows(MeteredToolExecutor executor) {
        try {
            executor.execute(request(), "qlawkus-rest");
        } catch (IllegalStateException expected) {
            return;
        }
        assertFalse(true, "the tool failure must propagate");
    }

    private static ToolExecutionRequest request() {
        return ToolExecutionRequest.builder().id("call-1").name("searchMemories").arguments(ARGUMENTS).build();
    }
}
