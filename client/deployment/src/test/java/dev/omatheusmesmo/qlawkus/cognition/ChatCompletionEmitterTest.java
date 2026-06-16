package dev.omatheusmesmo.qlawkus.cognition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Turn-completion detection: a turn is complete only when a final (text) assistant message is
 * appended, so passive post-turn work fires once per user turn rather than once per tool round.
 */
class ChatCompletionEmitterTest {

  @Test
  void finalAssistantMessageCompletesTheTurn() {
    assertTrue(ChatCompletionEmitter.isTurnComplete(List.of(
        AiMessage.from("Here is your answer."))));
  }

  @Test
  void userMessageAloneDoesNotCompleteTheTurn() {
    assertFalse(ChatCompletionEmitter.isTurnComplete(List.of(
        new UserMessage("what's the weather?"))));
  }

  @Test
  void toolRequestingAssistantMessageDoesNotCompleteTheTurn() {
    AiMessage toolCall = AiMessage.from(ToolExecutionRequest.builder()
        .id("1").name("getWeather").arguments("{}").build());
    assertFalse(ChatCompletionEmitter.isTurnComplete(List.of(toolCall)));
  }

  @Test
  void toolResultWithoutFinalAnswerDoesNotCompleteTheTurn() {
    assertFalse(ChatCompletionEmitter.isTurnComplete(List.of(
        ToolExecutionResultMessage.from("1", "getWeather", "sunny"))));
  }

  @Test
  void emptyBatchDoesNotCompleteTheTurn() {
    assertFalse(ChatCompletionEmitter.isTurnComplete(List.<ChatMessage>of()));
  }
}
