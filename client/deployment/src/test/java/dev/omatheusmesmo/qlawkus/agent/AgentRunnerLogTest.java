package dev.omatheusmesmo.qlawkus.agent;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.cognition.SoulEngine;
import dev.omatheusmesmo.qlawkus.logging.CapturedLogs;
import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

class AgentRunnerLogTest {

    private static final String SECRET = "sk-live-123456789";

    @Test
    void aFailedTurnEndsItsLineWithTheRootCauseOfTheFailure() {
        QlawkusAgent agent = mock(QlawkusAgent.class);
        when(agent.chat(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException(
                new InvocationTargetException(new RuntimeException("exceeded 10 sequential tool executions"))));
        AgentRunner runner = runner(agent);

        try (CapturedLogs logs = new CapturedLogs()) {
            assertThrows(IllegalStateException.class, () -> runner.chat("qlawkus-runner-fail", "hello"));

            List<String> turns = logs.info().stream().map(LogRecord::getMessage)
                    .filter(m -> m.startsWith("Agent turn") && m.contains("conversation=qlawkus-runner-fail"))
                    .toList();
            assertTrue(turns.size() == 1, () -> "one line for the turn: " + turns);
            assertTrue(turns.get(0).contains("outcome=error"), turns.get(0));
            assertTrue(turns.get(0).endsWith(" error=RuntimeException cause=exceeded 10 sequential tool executions"),
                    turns.get(0));
        }
    }

    @Test
    void theCauseIsRedactedBeforeItReachesTheLine() {
        QlawkusAgent agent = mock(QlawkusAgent.class);
        when(agent.chat(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException(
                "upstream rejected " + SECRET));
        AgentRunner runner = runner(agent);

        try (CapturedLogs logs = new CapturedLogs()) {
            assertThrows(IllegalStateException.class, () -> runner.chat("qlawkus-runner-secret", "hello"));

            String line = logs.info().stream().map(LogRecord::getMessage)
                    .filter(m -> m.startsWith("Agent turn") && m.contains("conversation=qlawkus-runner-secret"))
                    .findFirst().orElseThrow();
            assertTrue(line.endsWith(" error=IllegalStateException cause=upstream rejected [redacted]"), line);
            assertTrue(!line.contains(SECRET), line);
        }
    }

    private static AgentRunner runner(QlawkusAgent agent) {
        AgentRunner runner = new AgentRunner();
        runner.agent = agent;
        runner.soulEngine = mock(SoulEngine.class);
        runner.delivery = mock(AgentDeliveryContext.class);
        runner.redactor = redactor();
        return runner;
    }

    private static SecretRedactor redactor() {
        SecretRedactor redactor = mock(SecretRedactor.class);
        when(redactor.redact(anyString())).thenAnswer(invocation ->
                invocation.getArgument(0, String.class).replace(SECRET, "[redacted]"));
        return redactor;
    }
}
