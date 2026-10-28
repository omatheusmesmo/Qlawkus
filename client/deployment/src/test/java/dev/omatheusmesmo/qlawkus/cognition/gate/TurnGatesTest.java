package dev.omatheusmesmo.qlawkus.cognition.gate;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnGatesTest {

    private static final TurnQuestion QUESTION =
            new TurnQuestion("durable-fact", "Does this exchange state a durable fact about the user?");
    private static final List<ChatMessage> MESSAGES = List.of(new UserMessage("I prefer Vim"));

    @Test
    void aGateAnsweringNoSkipsTheTurnAndCountsASkip() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TurnGates gates = new TurnGates((question, messages) -> false, new AgentMeters(registry));

        assertFalse(gates.worthIt(QUESTION, MESSAGES));
        assertEquals(1.0, outcomeCount(registry, "durable-fact", AgentMeters.GATE_SKIP));
    }

    @Test
    void aGateAnsweringYesPassesTheTurnAndCountsAPass() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TurnGates gates = new TurnGates((question, messages) -> true, new AgentMeters(registry));

        assertTrue(gates.worthIt(QUESTION, MESSAGES));
        assertEquals(1.0, outcomeCount(registry, "durable-fact", AgentMeters.GATE_PASS));
    }

    @Test
    void aGateThatThrowsFailsOpenAndCountsAnError() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TurnGates gates = new TurnGates((question, messages) -> {
            throw new IllegalStateException("decision model unavailable");
        }, new AgentMeters(registry));

        assertTrue(gates.worthIt(QUESTION, MESSAGES), "a broken gate must not silence the extractor");
        assertEquals(1.0, outcomeCount(registry, "durable-fact", AgentMeters.GATE_ERROR));
    }

    @Test
    void theGateIsConsultedOnTheBatchWorkload() {
        AtomicReference<String> workload = new AtomicReference<>();
        TurnGates gates = new TurnGates((question, messages) -> {
            workload.set(WorkloadContext.current());
            return true;
        }, AgentMeters.disabled());

        gates.worthIt(QUESTION, MESSAGES);

        assertEquals(WorkloadContext.BATCH, workload.get());
    }

    @Test
    void theDefaultGateLetsEveryTurnThrough() {
        assertTrue(new PassThroughTurnGate().worthIt(QUESTION, MESSAGES));
    }

    @Test
    void aQuestionNeedsAnIdAndAText() {
        assertThrows(IllegalArgumentException.class, () -> new TurnQuestion(" ", "Is it?"));
        assertThrows(IllegalArgumentException.class, () -> new TurnQuestion("durable-fact", ""));
    }

    private static double outcomeCount(SimpleMeterRegistry registry, String question, String outcome) {
        return registry.get(AgentMeters.TURN_GATE)
                .tag("question", question)
                .tag("outcome", outcome)
                .counter()
                .count();
    }
}
