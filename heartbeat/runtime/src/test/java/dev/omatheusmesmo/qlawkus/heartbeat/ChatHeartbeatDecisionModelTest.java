package dev.omatheusmesmo.qlawkus.heartbeat;

import tools.jackson.databind.json.JsonMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatHeartbeatDecisionModelTest {

    private static final Map<String, Object> THINKING_OFF = Map.of("chat_template_kwargs", Map.of("enable_thinking", false));

    @Test
    void requestsAClosedChoiceWithoutTools() {
        var seen = new AtomicReference<ChatRequest>();
        var reply = modelReturning("CHECK_MEMORY", "openai", seen)
                .decide(Map.of("ownerInstructions", "Inspect recent memory."));
        assertEquals(HeartbeatDecision.Action.CHECK_MEMORY, reply.action());
        assertEquals(43, reply.tokens());
        assertEquals(48, seen.get().parameters().maxOutputTokens());
        assertTrue(seen.get().toolSpecifications().isEmpty());
    }

    @Test
    void turnsThinkingOffOnNvidiaSoTheTokenCapIsNotSpentReasoning() {
        var seen = new AtomicReference<ChatRequest>();
        modelReturning("NO_OP", "nvidia", seen).decide(Map.of("ownerInstructions", "Inspect."));
        var parameters = assertInstanceOf(OpenAiChatRequestParameters.class, seen.get().parameters());
        assertEquals(THINKING_OFF, parameters.customParameters());
    }

    @Test
    void sendsNoProviderSpecificParametersToOtherProviders() {
        for (String kind : new String[] {"openai", "ollama", ""}) {
            var seen = new AtomicReference<ChatRequest>();
            modelReturning("NO_OP", kind, seen).decide(Map.of("ownerInstructions", "Inspect."));
            ChatRequestParameters parameters = seen.get().parameters();
            assertFalse(parameters instanceof OpenAiChatRequestParameters, kind);
            assertNull(parameters.modelName(), kind);
        }
    }

    @Test
    void rejectsUnknownActionsAndProse() {
        assertThrows(IllegalArgumentException.class,
                () -> modelReturning("RUN_SHELL", "nvidia", new AtomicReference<>())
                        .decide(Map.of("ownerInstructions", "Inspect schedules.")));
        assertThrows(IllegalArgumentException.class,
                () -> modelReturning("CHECK_MEMORY then notify", "nvidia", new AtomicReference<>())
                        .decide(Map.of("ownerInstructions", "Inspect memory.")));
    }

    @Test
    void anEmptyReplyIsAFailedDecisionNotAnAction() {
        assertThrows(IllegalArgumentException.class,
                () -> modelReturning("", "nvidia", new AtomicReference<>())
                        .decide(Map.of("ownerInstructions", "Inspect memory.")));
    }

    private static ChatHeartbeatDecisionModel modelReturning(String output, String kind,
            AtomicReference<ChatRequest> seen) {
        return new ChatHeartbeatDecisionModel(new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                seen.set(request);
                return ChatResponse.builder().aiMessage(AiMessage.from(output)).tokenUsage(new TokenUsage(40, 3)).build();
            }
        }, JsonMapper.builder().build(), kind);
    }
}
