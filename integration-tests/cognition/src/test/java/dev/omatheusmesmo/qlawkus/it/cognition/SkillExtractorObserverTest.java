package dev.omatheusmesmo.qlawkus.it.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.omatheusmesmo.qlawkus.cognition.ChatCompletedEvent;
import dev.omatheusmesmo.qlawkus.cognition.SkillExtractorObserver;
import dev.omatheusmesmo.qlawkus.cognition.gate.TurnGate;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the auto-distillation path with a mocked model: the observer turns a model response
 * into a saved skill, ignores NONE, and swallows model failures.
 */
@QuarkusTest
class SkillExtractorObserverTest {

  @InjectMock
  ChatModel chatModel;

  @Inject
  SkillExtractorObserver observer;

  @Inject
  SkillStore skillStore;

  @InjectMock
  TurnGate turnGate;

  @Inject
  Event<ChatCompletedEvent> chatCompleted;

  @AfterEach
  void cleanup() {
    skillStore.delete("deploy-static-site");
  }

  @Test
  void onChatCompleted_gateSaysNo_modelIsNeverCalled() throws Exception {
    fireChatCompleted(new UserMessage("How do I deploy the site?"));

    verify(turnGate).worthIt(argThat(q -> "reusable-procedure".equals(q.id())), anyList());
    verify(chatModel, never()).chat(anyString());
  }

  @Test
  void onChatCompleted_gateSaysYes_distillationRunsAsBefore() throws Exception {
    when(turnGate.worthIt(argThat(q -> "reusable-procedure".equals(q.id())), anyList())).thenReturn(true);
    when(chatModel.chat(anyString())).thenReturn("NONE");

    fireChatCompleted(new UserMessage("How do I deploy the site?"));

    verify(chatModel).chat(anyString());
  }

  private void fireChatCompleted(ChatMessage... messages) throws Exception {
    chatCompleted.fireAsync(new ChatCompletedEvent(List.of(messages)))
        .toCompletableFuture().get(10, TimeUnit.SECONDS);
  }

  @Test
  void extractAndStore_savesDistilledSkill() {
    when(chatModel.chat(anyString())).thenReturn("""
        SKILL: deploy-static-site
        DESCRIPTION: Deploy the static site to GitHub Pages
        ---
        1. Build with mvn
        2. Push to the pages branch""");

    List<ChatMessage> messages = List.of(
        new UserMessage("How do I deploy the site?"),
        AiMessage.from("Build, then push to the pages branch."));

    Optional<Skill> distilled = observer.extractAndStore(messages);

    assertTrue(distilled.isPresent());
    assertEquals("deploy-static-site", distilled.get().name());
    Optional<Skill> stored = skillStore.get("deploy-static-site");
    assertTrue(stored.isPresent(), "distilled skill should be persisted");
    assertTrue(stored.get().body().contains("Push to the pages branch"));
  }

  @Test
  void extractAndStore_none_savesNothing() {
    when(chatModel.chat(anyString())).thenReturn("NONE");
    assertTrue(observer.extractAndStore(List.of(new UserMessage("just chatting"))).isEmpty());
  }

  @Test
  void extractAndStore_modelFailure_isSwallowed() {
    when(chatModel.chat(anyString())).thenThrow(new RuntimeException("LLM unavailable"));
    assertTrue(observer.extractAndStore(List.of(new UserMessage("hi"))).isEmpty());
  }
}
