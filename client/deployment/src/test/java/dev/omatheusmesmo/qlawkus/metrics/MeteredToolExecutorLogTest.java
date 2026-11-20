package dev.omatheusmesmo.qlawkus.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.omatheusmesmo.qlawkus.logging.CapturedLogs;
import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MeteredToolExecutorLogTest {

    private static final String ARGUMENTS = "{\"query\":\"my dentist appointment\"}";
    private static final String RESULT = "SECRET-RESULT-BODY";
    private static final String SECRET = "sk-live-123456789";

    @Test
    void oneInfoLinePerToolCallNamesTheToolAndItsOutcomeButNotTheArgumentsOrResult() {
        ToolExecutor delegate = (request, memoryId) -> RESULT;
        MeteredToolExecutor executor = new MeteredToolExecutor("searchMemories", delegate, AgentMeters.disabled(),
                redactor());

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
    void aFailingToolWarnsOnOneLineWithItsNameRootCauseAndRedactedMessage() {
        ToolExecutor delegate = (request, memoryId) -> {
            throw new IllegalStateException("wrapper", new IOException("disk gone with " + SECRET));
        };
        MeteredToolExecutor executor = new MeteredToolExecutor("deleteSkill", delegate, AgentMeters.disabled(),
                redactor());

        try (CapturedLogs logs = new CapturedLogs()) {
            assertThrows(executor);

            assertEquals(1, logs.info().size(), () -> "one line per failed call: " + logs.info());
            LogRecord warning = logs.warnings().get(0);
            assertEquals(Level.WARNING, warning.getLevel());
            String line = warning.getMessage();
            assertTrue(line.contains("Tool call deleteSkill outcome=error"), line);
            assertTrue(line.endsWith(" error=IOException cause=disk gone with [redacted]"), line);
            assertFalse(line.contains(SECRET), line);
            assertNull(warning.getThrown(), "the stack trace stays with the framework's own line");
        }
    }

    @Test
    void aReturnedResultLogsNoWarning() {
        ToolExecutor delegate = (request, memoryId) -> RESULT;
        MeteredToolExecutor executor = new MeteredToolExecutor("searchMemories", delegate, AgentMeters.disabled(),
                redactor());

        try (CapturedLogs logs = new CapturedLogs()) {
            executor.execute(request(), "qlawkus-rest");

            assertTrue(logs.warnings().isEmpty(), () -> "a returned result is not a failure: " + logs.warnings());
        }
    }

    private static SecretRedactor redactor() {
        SecretRedactor redactor = mock(SecretRedactor.class);
        when(redactor.redact(anyString())).thenAnswer(invocation ->
                invocation.getArgument(0, String.class).replace(SECRET, "[redacted]"));
        return redactor;
    }

    @Test
    void theToolNameIsInTheMdcOnlyWhileTheCallRuns() {
        ToolExecutor delegate = (request, memoryId) -> {
            assertEquals("searchMemories", MDC.get("tool"));
            return RESULT;
        };
        MeteredToolExecutor executor = new MeteredToolExecutor("searchMemories", delegate, AgentMeters.disabled(),
                redactor());

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
