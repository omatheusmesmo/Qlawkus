package dev.omatheusmesmo.qlawkus.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.LogProb;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.omatheusmesmo.qlawkus.cognition.gate.TurnGates;
import dev.omatheusmesmo.qlawkus.cognition.gate.TurnQuestion;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NvidiaTurnGateTest {

    private static final TurnQuestion DURABLE = new TurnQuestion("durable-fact", "Did the user state a durable fact?");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void normalizesTheYesAndNoTokenVariants() {
        var metadata = metadata(token("no", 0.60),
                token(" Yes", 0.15), token("yes", 0.05), token("no", 0.60), token("Different", 0.20));
        assertEquals(0.25, NvidiaDecisionModel.yesProbability(metadata), 1e-9);
    }

    @Test
    void disabledGatePassesWithoutAClient() {
        var gate = new NvidiaTurnGate(new Settings(false, 0.5), new NvidiaDecisionModel(new ChatModel() {}),
                new AgentMeters(registry),
                OpenTelemetry.noop().getTracer("test"));
        assertTrue(gate.worthIt(DURABLE, List.of(UserMessage.from("hello"))));
    }

    @Test
    void classifiesOnlyTurnTextAndUsesTheQuestionThreshold() {
        var meters = new AgentMeters(registry);
        var model = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                String input = ((UserMessage) request.messages().getLast()).singleText();
                assertTrue(input.contains("Always use dark mode"));
                assertTrue(input.contains("Understood"));
                assertFalse(input.contains("system prompt"));
                assertFalse(input.contains("tool output"));
                return ChatResponse.builder().aiMessage(AiMessage.from("Yes"))
                        .metadata(metadata(token("Yes", 0.7), token("Yes", 0.7), token("No", 0.3))).build();
            }
        };
        var gate = new NvidiaTurnGate(new Settings(true, 0.8), new NvidiaDecisionModel(model), meters,
                OpenTelemetry.noop().getTracer("test"));

        assertFalse(gate.worthIt(DURABLE, List.of(SystemMessage.from("system prompt"),
                UserMessage.from("Always use dark mode"), ToolExecutionResultMessage.from("id", "tool", "tool output"),
                AiMessage.from("Understood"))));
        assertEquals(1, registry.get(AgentMeters.TURN_GATE_DURATION).tag("question", "durable-fact").timer().count());
    }

    @Test
    void missingDistributionFailsOpenAndIsCounted() {
        var meters = new AgentMeters(registry);
        var model = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder().aiMessage(AiMessage.from("No")).build();
            }
        };
        var gate = new NvidiaTurnGate(new Settings(true, 0.5), new NvidiaDecisionModel(model), meters,
                OpenTelemetry.noop().getTracer("test"));

        assertTrue(new TurnGates(gate, meters).worthIt(DURABLE, List.of(UserMessage.from("hello"))));
        assertEquals(1, registry.get(AgentMeters.TURN_GATE)
                .tags("question", "durable-fact", "outcome", "error").counter().count());
    }

    private static LogProb token(String text, double probability) {
        return LogProb.builder().token(text).logprob(Math.log(probability)).build();
    }

    private static OpenAiChatResponseMetadata metadata(LogProb sampled, LogProb... alternatives) {
        return OpenAiChatResponseMetadata.builder().logProbs(List.of(LogProb.builder().token(sampled.token())
                .logprob(sampled.logprob()).topLogprobs(List.of(alternatives)).build())).build();
    }

    private record Settings(boolean enabled, double durableFactThreshold) implements NvidiaDecisionConfig {
        @Override
        public Optional<String> apiKey() {
            return Optional.empty();
        }

        @Override
        public String model() {
            return "fixture";
        }

        @Override
        public double reusableProcedureThreshold() {
            return 0.5;
        }

        @Override
        public double achievementThreshold() {
            return 0.5;
        }
    }
}
