package dev.omatheusmesmo.qlawkus.messaging.slack;

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

class SlackProviderAdapterTest {

    private SlackProviderAdapter adapter;
    private SlackApiClient apiClient;
    private SlackConfig config;
    private MessagingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        adapter = new SlackProviderAdapter();
        apiClient = Mockito.mock(SlackApiClient.class);
        config = Mockito.mock(SlackConfig.class);
        orchestrator = Mockito.mock(MessagingOrchestrator.class);

        adapter.apiClient = apiClient;
        adapter.config = config;
        adapter.orchestrator = orchestrator;

        when(config.botToken()).thenReturn("xoxb-test-token");
        when(orchestrator.process(any())).thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void providerId_isSlack() {
        assertEquals("slack", adapter.providerId());
    }

    @Test
    void supportedFormat_isSlackMarkdown() {
        assertEquals(MessagingFormat.SLACK_MARKDOWN, adapter.supportedFormat());
    }

    @Test
    void mapEvent_mapsFieldsCorrectly() {
        SlackEvent.InnerEvent event = new SlackEvent.InnerEvent(
                "message", "U12345", "hello world", "C98765", "im");

        MessagingMessage msg = adapter.mapEvent(event);

        assertEquals("slack", msg.providerId());
        assertEquals("C98765", msg.chatId());
        assertEquals("U12345", msg.userId());
        assertEquals("hello world", msg.text());
    }

    @Test
    void mapEvent_nullText_mapsToEmptyString() {
        SlackEvent.InnerEvent event = new SlackEvent.InnerEvent(
                "message", "U12345", null, "C98765", "im");

        assertEquals("", adapter.mapEvent(event).text());
    }

    @Test
    void send_callsApiClientWithBearerToken() {
        adapter.send("C98765", "hello").await().indefinitely();

        verify(apiClient).postMessage(
                eq("Bearer xoxb-test-token"),
                argThat(req -> "C98765".equals(req.channel())
                        && "hello".equals(req.text())
                        && req.markdown()));
    }

    @Test
    void send_apiThrows_completesWithoutPropagating() {
        doThrow(new RuntimeException("slack error")).when(apiClient).postMessage(any(), any());

        assertDoesNotThrow(() -> adapter.send("C98765", "hi").await().indefinitely());
    }
}
