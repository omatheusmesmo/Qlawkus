package dev.omatheusmesmo.qlawkus.messaging.whatsapp;

import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.MessagingOrchestrator;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsAppProviderAdapterTest {

    private WhatsAppProviderAdapter adapter;
    private WhatsAppApiClient apiClient;
    private WhatsAppConfig config;
    private MessagingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        adapter = new WhatsAppProviderAdapter();
        apiClient = Mockito.mock(WhatsAppApiClient.class);
        config = Mockito.mock(WhatsAppConfig.class);
        orchestrator = Mockito.mock(MessagingOrchestrator.class);

        adapter.apiClient = apiClient;
        adapter.config = config;
        adapter.orchestrator = orchestrator;

        when(config.accessToken()).thenReturn("access-token");
        when(config.phoneNumberId()).thenReturn("phone-id");
        when(orchestrator.process(any())).thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void providerId_isWhatsApp() {
        assertEquals("whatsapp", adapter.providerId());
    }

    @Test
    void supportedFormat_isWhatsAppHtml() {
        assertEquals(MessagingFormat.WHATSAPP_HTML, adapter.supportedFormat());
    }

    @Test
    void mapEvent_textMessage_mapsCorrectly() {
        WhatsAppEvent.Message message = new WhatsAppEvent.Message(
                "msg-1", "5511999999999", "text",
                new WhatsAppEvent.Text("hello"), null);

        MessagingMessage msg = adapter.mapEvent(message);

        assertEquals("whatsapp", msg.providerId());
        assertEquals("5511999999999", msg.chatId());
        assertEquals("5511999999999", msg.userId());
        assertEquals("hello", msg.text());
    }

    @Test
    void mapEvent_audioMessage_textIsEmpty() {
        WhatsAppEvent.Message message = new WhatsAppEvent.Message(
                "msg-2", "5511999999999", "audio",
                null, new WhatsAppEvent.Audio("file-id"));

        MessagingMessage msg = adapter.mapEvent(message);
        assertEquals("", msg.text());
    }

    @Test
    void send_callsApiClientWithBearerToken() {
        adapter.send("5511999999999", "hello").await().indefinitely();

        verify(apiClient).sendMessage(
                eq("phone-id"),
                eq("Bearer access-token"),
                argThat(req -> "5511999999999".equals(req.to()) && "hello".equals(req.text().body())));
    }

    @Test
    void send_apiThrows_completesWithoutPropagating() {
        doThrow(new RuntimeException("api error")).when(apiClient).sendMessage(any(), any(), any());

        assertDoesNotThrow(() -> adapter.send("5511999999999", "hi").await().indefinitely());
    }
}
