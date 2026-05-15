package dev.omatheusmesmo.qlawkus.messaging.discord;

import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.MessagingOrchestrator;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordProviderAdapterTest {

    private DiscordProviderAdapter adapter;
    private MessagingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        adapter = new DiscordProviderAdapter();
        orchestrator = Mockito.mock(MessagingOrchestrator.class);
        adapter.orchestrator = orchestrator;

        when(orchestrator.process(any())).thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void providerId_isDiscord() {
        assertEquals("discord", adapter.providerId());
    }

    @Test
    void supportedFormat_isDiscordMarkdown() {
        assertEquals(MessagingFormat.DISCORD_MARKDOWN, adapter.supportedFormat());
    }

    @Test
    void receive_delegatesToOrchestrator() {
        MessagingMessage msg = MessagingMessage.text("discord", "chan-1", "user-1", "hello");

        adapter.receive(msg).await().indefinitely();

        verify(orchestrator).process(msg);
    }

    @Test
    void send_returnsVoidWhenGatewayNotConnected() {
        adapter.gatewayClient = null;

        adapter.send("chan-1", "hello").await().indefinitely();
    }
}
