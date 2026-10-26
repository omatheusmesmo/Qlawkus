package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Which tier answers a failing primary, decided by policy rather than by the wire: a background workload
 * that the bulkhead rejects reaches no tier at all, and a kind with the secondary disabled goes straight
 * from the primary to Ollama. Scripted models count their calls, so the assertion is on which tier was
 * reached.
 */
@QuarkusTest
class FallbackTierPolicyTest {

    @Inject
    PrimaryChatGuard chatGuard;

    @Inject
    SecondaryChatGuard secondaryGuard;

    @BeforeEach
    void resetBreakers() {
        CircuitBreakerMaintenance.get().reset(ModelFallbackConfig.CIRCUIT_BREAKER_CHAT);
        CircuitBreakerMaintenance.get().reset(ModelFallbackConfig.CIRCUIT_BREAKER_SECONDARY_CHAT);
    }

    @Test
    void aBatchCallRejectedByTheBulkheadReachesNoTier() {
        AtomicInteger secondary = new AtomicInteger();
        AtomicInteger ollama = new AtomicInteger();
        ChatModel primary = scripted(() -> {
            throw new BulkheadException("throttled");
        });
        FallbackChatModel model = new FallbackChatModel(
                primary, counting(secondary, "from-secondary"), counting(ollama, "from-ollama"),
                chatGuard, secondaryGuard, enabled(true));

        assertThrows(BulkheadException.class,
                () -> WorkloadContext.callAs(WorkloadContext.BATCH, () -> model.chat(request())));

        assertEquals(0, secondary.get(), "a rejected background call must not spend the secondary");
        assertEquals(0, ollama.get(), "a rejected background call must not spend the Ollama fallback");
    }

    @Test
    void withTheSecondaryDisabledAFailingPrimaryGoesStraightToOllama() {
        AtomicInteger secondary = new AtomicInteger();
        AtomicInteger ollama = new AtomicInteger();
        ChatModel primary = scripted(() -> {
            throw new RuntimeException("primary is down");
        });
        FallbackChatModel model = new FallbackChatModel(
                primary, counting(secondary, "from-secondary"), counting(ollama, "from-ollama"),
                chatGuard, secondaryGuard, enabled(false));

        assertEquals("from-ollama", model.chat(request()).aiMessage().text());

        assertEquals(0, secondary.get(), "a kind without a same-provider model must never call one");
        assertEquals(1, ollama.get());
    }

    @Test
    void aFailingSecondaryFallsToOllama() {
        AtomicInteger ollama = new AtomicInteger();
        ChatModel primary = scripted(() -> {
            throw new RuntimeException("primary is down");
        });
        ChatModel secondary = scripted(() -> {
            throw new RuntimeException("secondary is down");
        });
        FallbackChatModel model = new FallbackChatModel(
                primary, secondary, counting(ollama, "from-ollama"), chatGuard, secondaryGuard, enabled(true));

        assertEquals("from-ollama", model.chat(request()).aiMessage().text());
        assertEquals(1, ollama.get());
    }

    private static ModelFallbackConfig enabled(boolean secondaryEnabled) {
        ModelFallbackConfig config = Mockito.mock(ModelFallbackConfig.class);
        Mockito.when(config.fallbackEnabled()).thenReturn(true);
        Mockito.when(config.secondaryEnabled()).thenReturn(secondaryEnabled);
        return config;
    }

    private static ChatModel counting(AtomicInteger calls, String text) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                calls.incrementAndGet();
                return response(text);
            }
        };
    }

    private static ChatModel scripted(Runnable behaviour) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                behaviour.run();
                return response("unreachable");
            }
        };
    }

    private static ChatResponse response(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    private static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("hi")).build();
    }
}
