package dev.omatheusmesmo.qlawkus.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.logging.CapturedLogs;
import java.util.List;
import java.util.logging.LogRecord;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.Test;

class AgentTurnLogTest {

    @Test
    void oneInfoLinePerTurnCarriesTheShapeOfTheTurnWithoutContent() {
        try (CapturedLogs logs = new CapturedLogs()) {
            AgentTurn turn = AgentTurn.open("qlawkus-rest", "rest", false);
            try (AgentTurn.Binding ignored = turn.bind()) {
                turn.tool("searchMemories");
                turn.tool("remember");
                turn.facts(3);
                turn.usage(120, 45);
                turn.tier("secondary");
            }
            turn.close("ok");

            List<String> turns = turnLines(logs);
            assertEquals(1, turns.size(), "one line per turn: " + turns);
            String line = turns.get(0);
            assertTrue(line.contains("channel=rest"), line);
            assertTrue(line.contains("conversation=qlawkus-rest"), line);
            assertTrue(line.contains("workload=interactive"), line);
            assertTrue(line.contains("tier=secondary"), line);
            assertTrue(line.contains("tools=2"), line);
            assertTrue(line.contains("searchMemories") && line.contains("remember"), line);
            assertTrue(line.contains("facts=3"), line);
            assertTrue(line.contains("tokens=120/45"), line);
            assertTrue(line.contains("outcome=ok"), line);
            assertTrue(line.contains("duration="), line);
        }
    }

    @Test
    void unknownTokensAndTierAreSaidOutright() {
        try (CapturedLogs logs = new CapturedLogs()) {
            AgentTurn turn = AgentTurn.open("qlawkus-rest", "discord", false);
            turn.close("error");

            String line = turnLines(logs).get(0);
            assertTrue(line.contains("tokens=unknown"), line);
            assertTrue(line.contains("tier=unknown"), line);
            assertTrue(line.contains("tools=0"), line);
            assertTrue(line.contains("outcome=error"), line);
        }
    }

    @Test
    void channelAndConversationAreInTheMdcOnlyWhileBound() {
        AgentTurn turn = AgentTurn.open("qlawkus-shared", "telegram", false);
        try (AgentTurn.Binding ignored = turn.bind()) {
            assertEquals("telegram", MDC.get("channel"));
            assertEquals("qlawkus-shared", MDC.get("conversation"));
            assertEquals("interactive", MDC.get("workload"));
        }
        turn.close("ok");

        assertNull(MDC.get("channel"));
        assertNull(MDC.get("conversation"));
    }

    @Test
    void closingTwiceLogsOnce() {
        try (CapturedLogs logs = new CapturedLogs()) {
            AgentTurn turn = AgentTurn.open("qlawkus-rest", "rest", false);
            turn.close("ok");
            turn.close("ok");

            assertEquals(1, turnLines(logs).size());
        }
    }

    @Test
    void aTurnIsFindableByConversationOnlyUntilItCloses() {
        AgentTurn turn = AgentTurn.open("qlawkus-lookup", "rest", false);
        assertTrue(AgentTurn.forConversation("qlawkus-lookup").isPresent());

        turn.close("ok");

        assertFalse(AgentTurn.forConversation("qlawkus-lookup").isPresent());
    }

    /**
     * Only the turn lines: other tests in the same JVM leave background threads that log at INFO
     * while this one captures.
     */
    private static List<String> turnLines(CapturedLogs logs) {
        return logs.info().stream().map(LogRecord::getMessage).filter(m -> m.startsWith("Agent turn")).toList();
    }
}
