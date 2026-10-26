package dev.omatheusmesmo.qlawkus.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.omatheusmesmo.qlawkus.logging.CapturedLogs;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import java.util.List;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

class SemanticExtractorLoggingTest {

    private static final String FACT = "User is based in Sao Paulo and prefers dark theme";

    @Test
    void factTextNeverReachesInfo() {
        SemanticExtractorObserver observer = observer("- " + FACT + "\n- User works with Java");
        try (CapturedLogs logs = new CapturedLogs()) {
            observer.extractAndStore(conversation());

            for (LogRecord record : logs.info()) {
                assertFalse(record.getMessage().contains("Sao Paulo"),
                        "fact text reached INFO: " + record.getMessage());
                assertFalse(record.getMessage().contains("Java"),
                        "fact text reached INFO: " + record.getMessage());
            }
        }
    }

    @Test
    void infoCarriesOnlyTheCountOfStoredFacts() {
        SemanticExtractorObserver observer = observer("- " + FACT + "\n- User works with Java");
        try (CapturedLogs logs = new CapturedLogs()) {
            observer.extractAndStore(conversation());

            List<LogRecord> info = logs.info();
            assertEquals(1, info.size(), "one count line per extraction: " + info);
            assertTrue(info.get(0).getMessage().contains("2"), info.get(0).getMessage());
        }
    }

    private static List<ChatMessage> conversation() {
        return List.of(UserMessage.from("hi"));
    }

    private static SemanticExtractorObserver observer(String extraction) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(anyString())).thenReturn(extraction);
        SemanticExtractorObserver observer = new SemanticExtractorObserver();
        observer.chatModel = chatModel;
        observer.factStore = mock(FactStore.class);
        return observer;
    }
}
