package dev.omatheusmesmo.qlawkus.heartbeat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHeartbeatDecisionModelTest {

    @Test
    void requestsAClosedChoiceWithoutThinkingOrTools() {
        var model = modelReturning("CHECK_MEMORY");
        var reply = model.decide(Map.of("ownerInstructions", "Inspect recent memory."));
        assertEquals(HeartbeatDecision.Action.CHECK_MEMORY, reply.action());
        assertEquals(43, reply.tokens());
    }

    @Test
    void rejectsUnknownActionsAndProse() {
        assertThrows(IllegalArgumentException.class,
                () -> modelReturning("RUN_SHELL").decide(Map.of("ownerInstructions", "Inspect schedules.")));
        assertThrows(IllegalArgumentException.class,
                () -> modelReturning("CHECK_MEMORY then notify").decide(Map.of("ownerInstructions", "Inspect memory.")));
    }

    private static LocalHeartbeatDecisionModel modelReturning(String output) {
        return new LocalHeartbeatDecisionModel(new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                var parameters = (OllamaChatRequestParameters) request.parameters();
                assertFalse(parameters.think());
                assertEquals(48, parameters.maxOutputTokens());
                assertTrue(request.toolSpecifications().isEmpty());
                return ChatResponse.builder().aiMessage(AiMessage.from(output)).tokenUsage(new TokenUsage(40, 3)).build();
            }
        }, new ObjectMapper());
    }
}
