package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.messaging.format.FormatterRegistry;
import dev.omatheusmesmo.qlawkus.messaging.format.MessageFormatter;
import dev.omatheusmesmo.qlawkus.messaging.logging.MessagingLogCapture;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceLogTest {

    private static final byte[] AUDIO = new byte[] {1, 2, 3};

    private NotificationService service;
    private ProviderRegistry providerRegistry;
    private ChunkingService chunkingService;
    private MessagingProvider provider;

    @BeforeEach
    void setUp() {
        service = new NotificationService();
        providerRegistry = Mockito.mock(ProviderRegistry.class);
        FormatterRegistry formatterRegistry = Mockito.mock(FormatterRegistry.class);
        chunkingService = Mockito.mock(ChunkingService.class);
        provider = Mockito.mock(MessagingProvider.class);

        service.providerRegistry = providerRegistry;
        service.formatterRegistry = formatterRegistry;
        service.chunkingService = chunkingService;

        MessageFormatter passThrough = new MessageFormatter() {
            @Override public MessagingFormat format() { return MessagingFormat.PLAIN_TEXT; }
            @Override public String format(String text) { return text == null ? "" : text; }
        };
        when(formatterRegistry.forFormat(any())).thenReturn(passThrough);
        when(providerRegistry.getProvider("telegram")).thenReturn(Optional.of(provider));
        when(provider.supportedFormat()).thenReturn(MessagingFormat.PLAIN_TEXT);
    }

    @Test
    void deliveredReplyLogsPartsKindAndDurationWithChannelAndConversationInMdc() {
        when(chunkingService.chunk(any(), eq("telegram"))).thenReturn(List.of("secret part one", "secret part two"));
        when(provider.send(any(), any())).thenReturn(Uni.createFrom().voidItem());

        try (MessagingLogCapture logs = new MessagingLogCapture()) {
            service.send("telegram", "chat-log-1", "secret part one secret part two", "conv-log-1")
                    .await().indefinitely();

            List<MessagingLogCapture.Line> delivered = logs.containing("Reply delivered");
            assertEquals(1, delivered.size(), () -> "expected one delivered line, got " + logs.lines());
            String line = delivered.get(0).message();
            assertTrue(line.contains("provider=telegram"), line);
            assertTrue(line.contains("chatId=chat-log-1"), line);
            assertTrue(line.contains("parts=2"), line);
            assertTrue(line.contains("kind=text"), line);
            assertTrue(line.matches(".*elapsedMs=\\d+.*"), line);
            assertEquals("telegram", delivered.get(0).mdc().get("channel"));
            assertEquals("conv-log-1", delivered.get(0).mdc().get("conversation"));
            assertNoContent(logs, "secret");
        }
    }

    @Test
    void failedDeliveryWarnsWithExceptionClassAndNoReplyText() {
        when(chunkingService.chunk(any(), eq("telegram"))).thenReturn(List.of("secret reply body"));
        when(provider.send(any(), any())).thenReturn(
                Uni.createFrom().failure(new IllegalStateException("upstream said: secret reply body")));

        try (MessagingLogCapture logs = new MessagingLogCapture()) {
            assertDoesNotThrow(() -> service.send("telegram", "chat-log-2", "secret reply body", "conv-log-2")
                    .await().indefinitely());

            List<MessagingLogCapture.Line> failed = logs.containing("Reply delivery failed");
            assertEquals(1, failed.size(), () -> "expected one failure warning, got " + logs.lines());
            assertEquals(java.util.logging.Level.WARNING, failed.get(0).level());
            String line = failed.get(0).message();
            assertTrue(line.contains("chatId=chat-log-2"), line);
            assertTrue(line.contains("sentParts=0 of 1"), line);
            assertTrue(line.contains("exception=IllegalStateException"), line);
            assertEquals("telegram", failed.get(0).mdc().get("channel"));
            assertNoContent(logs, "secret");
            assertTrue(logs.containing("Reply delivered").isEmpty());
        }
    }

    @Test
    void deliveredVoiceReplyLogsKindVoice() {
        when(provider.sendVoice(any(), any(), any(), any())).thenReturn(Uni.createFrom().voidItem());

        try (MessagingLogCapture logs = new MessagingLogCapture()) {
            service.sendVoice("telegram", "chat-log-3", AUDIO, "resposta.mp3", "secret caption", "conv-log-3")
                    .await().indefinitely();

            List<MessagingLogCapture.Line> delivered = logs.containing("Reply delivered");
            assertEquals(1, delivered.size(), () -> "expected one delivered line, got " + logs.lines());
            assertTrue(delivered.get(0).message().contains("kind=voice"), delivered.get(0).message());
            assertTrue(delivered.get(0).message().contains("parts=1"), delivered.get(0).message());
            assertNoContent(logs, "secret");
        }
    }

    @Test
    void failedVoiceReplyWarnsAndStillFailsSoTheCallerCanFallBackToText() {
        when(provider.sendVoice(any(), any(), any(), any()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("tts upstream down")));

        try (MessagingLogCapture logs = new MessagingLogCapture()) {
            assertThrows(IllegalStateException.class, () -> service.sendVoice(
                    "telegram", "chat-log-4", AUDIO, "resposta.mp3", "secret caption", "conv-log-4")
                    .await().indefinitely());

            List<MessagingLogCapture.Line> failed = logs.containing("Reply delivery failed");
            assertEquals(1, failed.size(), () -> "expected one failure warning, got " + logs.lines());
            assertTrue(failed.get(0).message().contains("kind=voice"), failed.get(0).message());
            assertTrue(failed.get(0).message().contains("exception=IllegalStateException"), failed.get(0).message());
            assertNoContent(logs, "secret");
        }
    }

    private static void assertNoContent(MessagingLogCapture logs, String fragment) {
        assertTrue(logs.lines().stream().noneMatch(line -> line.message() != null && line.message().contains(fragment)),
                () -> "reply text reached a log line: " + logs.lines());
    }
}
