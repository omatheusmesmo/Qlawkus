package dev.omatheusmesmo.qlawkus.messaging.telegram;

import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.MessagingOrchestrator;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TelegramProviderAdapterTest {

    private TelegramProviderAdapter adapter;
    private TelegramBotClient botClient;
    private TelegramConfig config;
    private MessagingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        adapter = new TelegramProviderAdapter();
        botClient = Mockito.mock(TelegramBotClient.class);
        config = Mockito.mock(TelegramConfig.class);
        orchestrator = Mockito.mock(MessagingOrchestrator.class);

        adapter.botClient = botClient;
        adapter.config = config;
        adapter.orchestrator = orchestrator;

        when(config.botToken()).thenReturn("123:token");
        when(orchestrator.process(any())).thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void providerId_isTelegram() {
        assertEquals("telegram", adapter.providerId());
    }

    @Test
    void supportedFormat_isTelegramMarkdownV2() {
        assertEquals(MessagingFormat.TELEGRAM_MARKDOWN_V2, adapter.supportedFormat());
    }

    @Test
    void mapUpdate_mapsFieldsCorrectly() {
        TelegramUpdate update = new TelegramUpdate(1L, new TelegramUpdate.TelegramMessage(
                10L,
                new TelegramUpdate.TelegramUser(42L, "Alice", "alice"),
                new TelegramUpdate.TelegramChat(42L, "private"),
                "hello",
                null
        ));

        MessagingMessage msg = adapter.mapUpdate(update);

        assertEquals("telegram", msg.providerId());
        assertEquals("42", msg.chatId());
        assertEquals("42", msg.userId());
        assertEquals("hello", msg.text());
        assertTrue(msg.audio().isEmpty());
    }

    @Test
    void mapUpdate_nullText_mapsToEmptyString() {
        TelegramUpdate update = new TelegramUpdate(1L, new TelegramUpdate.TelegramMessage(
                10L,
                new TelegramUpdate.TelegramUser(1L, "Bob", "bob"),
                new TelegramUpdate.TelegramChat(1L, "private"),
                null,
                new TelegramUpdate.TelegramVoice("file-id", 5)
        ));

        MessagingMessage msg = adapter.mapUpdate(update);
        assertEquals("", msg.text());
    }

    @Test
    void send_callsBotClientWithTokenAndMarkdownV2() {
        adapter.send("chat-1", "hello world").await().indefinitely();

        verify(botClient).sendMessage(
                eq("123:token"),
                argThat(req -> req.chatId().equals("chat-1")
                        && req.text().equals("hello world")
                        && "MarkdownV2".equals(req.parseMode())));
    }

    @Test
    void send_botClientThrows_completesWithoutPropagating() {
        doThrow(new RuntimeException("network error")).when(botClient).sendMessage(any(), any());

        assertDoesNotThrow(() -> adapter.send("chat-1", "hello").await().indefinitely());
    }

    @Test
    void receive_delegatesToOrchestrator() {
        MessagingMessage msg = MessagingMessage.text("telegram", "chat-1", "user-1", "hello");
        adapter.receive(msg).await().indefinitely();

        verify(orchestrator).process(msg);
    }
}
