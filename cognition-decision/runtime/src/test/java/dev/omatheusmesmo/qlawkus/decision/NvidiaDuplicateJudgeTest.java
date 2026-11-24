package dev.omatheusmesmo.qlawkus.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.LogProb;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.omatheusmesmo.qlawkus.cognition.review.DuplicateJudges;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;

class NvidiaDuplicateJudgeTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private NvidiaDuplicateJudge judge(boolean enabled, ChatModel chat) {
        return new NvidiaDuplicateJudge(new Settings(enabled), new NvidiaDecisionModel(chat),
                new AgentMeters(registry), OpenTelemetry.noop().getTracer("test"));
    }

    private static ChatModel answering(double yes) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                String input = ((UserMessage) request.messages().getLast()).singleText();
                assertTrue(input.contains("Statement A: User prefers dark theme"));
                assertTrue(input.contains("Statement B: User no longer prefers dark theme"));
                return ChatResponse.builder().aiMessage(AiMessage.from("Yes"))
                        .metadata(metadata(yes)).build();
            }
        };
    }

    @Test
    void aPairScoringBelowTheThresholdIsNotADuplicate() {
        assertFalse(judge(true, answering(0.6)).sameFact("User prefers dark theme", "User no longer prefers dark theme"));
    }

    @Test
    void aPairScoringAboveTheThresholdIsADuplicate() {
        assertTrue(judge(true, answering(0.99)).sameFact("User prefers dark theme", "User no longer prefers dark theme"));
    }

    @Test
    void disabledTrustsTheDistanceLikeTheReviewDidBeforeAJudgeExisted() {
        assertTrue(judge(false, new ChatModel() {}).sameFact("a", "b"));
    }

    @Test
    void aMissingDistributionKeepsBothFactsAndIsCounted() {
        var failing = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder().aiMessage(AiMessage.from("No")).build();
            }
        };
        var judge = judge(true, failing);

        assertThrows(IllegalStateException.class, () -> judge.sameFact("a", "b"));
        assertFalse(new DuplicateJudges(judge, new AgentMeters(registry)).sameFact("a", "b"));
        assertEquals(1, registry.get(AgentMeters.TURN_GATE)
                .tags("question", "duplicate-fact", "outcome", "error").counter().count());
    }

    private static OpenAiChatResponseMetadata metadata(double yes) {
        var yesToken = LogProb.builder().token("Yes").logprob(Math.log(yes)).build();
        var noToken = LogProb.builder().token("No").logprob(Math.log(1 - yes)).build();
        return OpenAiChatResponseMetadata.builder().logProbs(List.of(LogProb.builder().token("Yes")
                .logprob(Math.log(yes)).topLogprobs(List.of(yesToken, noToken)).build())).build();
    }

    private record Settings(boolean enabled) implements NvidiaDecisionConfig {
        @Override
        public Optional<String> apiKey() {
            return Optional.empty();
        }

        @Override
        public String model() {
            return "fixture";
        }

        @Override
        public double durableFactThreshold() {
            return 0.5;
        }

        @Override
        public double reusableProcedureThreshold() {
            return 0.5;
        }

        @Override
        public double achievementThreshold() {
            return 0.5;
        }

        @Override
        public double duplicateThreshold() {
            return 0.9;
        }
    }
}
