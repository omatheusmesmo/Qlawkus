package dev.omatheusmesmo.qlawkus.it.brag;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.omatheusmesmo.qlawkus.tools.brag.AchievementProcessor;
import dev.omatheusmesmo.qlawkus.tools.brag.BragEntry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@Execution(ExecutionMode.SAME_THREAD)
class AchievementProcessorTest {

    @InjectMock
    ChatModel chatModel;

    @Inject
    AchievementProcessor processor;

    @AfterEach
    @Transactional
    void cleanup() {
        BragEntry.deleteAll();
    }

    @Test
    void detectAchievement_returnsAchievementForEngineeringTask() {
        when(chatModel.chat(anyString())).thenReturn("Fixed null pointer exception in authentication service");

        List<ChatMessage> messages = List.of(
                new UserMessage("I fixed the NPE in auth"),
                AiMessage.from("Great, the fix is applied."));

        String result = processor.detectAchievement(messages);

        assertNotNull(result);
        assertEquals("Fixed null pointer exception in authentication service", result);
    }

    @Test
    void detectAchievement_returnsNullForNonTaskConversation() {
        when(chatModel.chat(anyString())).thenReturn("");

        List<ChatMessage> messages = List.of(
                new UserMessage("Hello, how are you?"),
                AiMessage.from("I'm doing well!"));

        String result = processor.detectAchievement(messages);

        assertNull(result);
    }

    @Test
    void detectAchievement_returnsNullForNoneResponse() {
        when(chatModel.chat(anyString())).thenReturn("None");

        List<ChatMessage> messages = List.of(
                new UserMessage("Tell me a joke"),
                AiMessage.from("Why do programmers prefer dark mode?"));

        String result = processor.detectAchievement(messages);

        assertNull(result);
    }

    @Test
    void detectAchievement_handlesLlmException() {
        when(chatModel.chat(anyString())).thenThrow(new RuntimeException("LLM unavailable"));

        List<ChatMessage> messages = List.of(
                new UserMessage("I deployed the service"),
                AiMessage.from("Good work."));

        String result = processor.detectAchievement(messages);

        assertNull(result);
    }

    @Test
    @Transactional
    void extractAndStore_persistAchievementWithImpact() {
        when(chatModel.chat(anyString()))
                .thenReturn("Deployed hotfix to production")
                .thenReturn("Reduced customer-facing error rate by 60%");

        List<ChatMessage> messages = List.of(
                new UserMessage("I deployed the hotfix"),
                AiMessage.from("Good work."));

        processor.extractAndStore(messages);

        long count = BragEntry.count("achievement = ?1", "Deployed hotfix to production");
        assertEquals(1, count);

        BragEntry entry = BragEntry.find("achievement", "Deployed hotfix to production").firstResult();
        assertNotNull(entry.impact);
        assertEquals("Reduced customer-facing error rate by 60%", entry.impact);
    }

    @Test
    @Transactional
    void extractAndStore_skipsWhenNoAchievementDetected() {
        when(chatModel.chat(anyString())).thenReturn("");

        List<ChatMessage> messages = List.of(
                new UserMessage("Hello"),
                AiMessage.from("Hi there!"));

        processor.extractAndStore(messages);

        assertEquals(0, BragEntry.count());
    }

    @Test
    @Transactional
    void extractAndStore_handlesImpactTranslationFailure() {
        when(chatModel.chat(anyString()))
                .thenReturn("Refactored service layer")
                .thenThrow(new RuntimeException("LLM unavailable"));

        List<ChatMessage> messages = List.of(
                new UserMessage("I refactored the service layer"),
                AiMessage.from("Nice improvement."));

        processor.extractAndStore(messages);

        BragEntry entry = BragEntry.find("achievement", "Refactored service layer").firstResult();
        assertNotNull(entry);
        assertNull(entry.impact);
    }

    @Test
    @Transactional
    void persistAchievement_skipsDuplicate() {
        when(chatModel.chat(anyString())).thenReturn("Impact statement");

        processor.persistAchievement("Fixed auth bug");
        processor.persistAchievement("Fixed auth bug");

        assertEquals(1, BragEntry.count("achievement", "Fixed auth bug"));
    }

    @Test
    @Transactional
    void persistAchievement_setsRepoToNull() {
        when(chatModel.chat(anyString())).thenReturn("Improved reliability");

        processor.persistAchievement("Migrated to new cache layer");

        BragEntry entry = BragEntry.find("achievement", "Migrated to new cache layer").firstResult();
        assertNull(entry.repo);
    }

    @Test
    @Transactional
    void persistAchievement_setsDateToToday() {
        when(chatModel.chat(anyString())).thenReturn("Impact");

        processor.persistAchievement("Added monitoring dashboards");

        BragEntry entry = BragEntry.find("achievement", "Added monitoring dashboards").firstResult();
        assertEquals(LocalDate.now(ZoneOffset.UTC), entry.date);
    }
}
