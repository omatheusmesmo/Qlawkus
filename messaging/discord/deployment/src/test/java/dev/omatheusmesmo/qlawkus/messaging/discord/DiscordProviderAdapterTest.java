package dev.omatheusmesmo.qlawkus.messaging.discord;

import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.MessagingOrchestrator;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DiscordProviderAdapterTest {

    private DiscordProviderAdapter adapter;
    private DiscordApiClient apiClient;
    private DiscordConfig config;
    private MessagingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        adapter = new DiscordProviderAdapter();
        apiClient = Mockito.mock(DiscordApiClient.class);
        config = Mockito.mock(DiscordConfig.class);
        orchestrator = Mockito.mock(MessagingOrchestrator.class);

        adapter.apiClient = apiClient;
        adapter.config = config;
        adapter.orchestrator = orchestrator;

        when(config.botToken()).thenReturn("bot-token");
        when(config.applicationId()).thenReturn("app-123");
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
    void mapInteraction_mapsFieldsFromUserDirectly() {
        DiscordInteraction interaction = new DiscordInteraction(
                "id-1", "token-abc", 2,
                new DiscordInteraction.DiscordUser("user-42", "alice"),
                null, "channel-7",
                new DiscordInteraction.DiscordData("ask",
                        List.of(new DiscordInteraction.DiscordOption("text", "hello")))
        );

        MessagingMessage msg = adapter.mapInteraction(interaction);

        assertEquals("discord", msg.providerId());
        assertEquals("channel-7", msg.chatId());
        assertEquals("user-42", msg.userId());
        assertEquals("hello", msg.text());
    }

    @Test
    void mapInteraction_fallsBackToMemberUser() {
        DiscordInteraction interaction = new DiscordInteraction(
                "id-1", "token", 2, null,
                new DiscordInteraction.DiscordMember(
                        new DiscordInteraction.DiscordUser("member-99", "bob")),
                "ch-5",
                new DiscordInteraction.DiscordData("ask", List.of(
                        new DiscordInteraction.DiscordOption("query", "world")))
        );

        MessagingMessage msg = adapter.mapInteraction(interaction);
        assertEquals("member-99", msg.userId());
        assertEquals("world", msg.text());
    }

    @Test
    void send_callsApiClientWithBotToken() {
        adapter.send("interaction-token", "response text").await().indefinitely();

        verify(apiClient).editOriginalResponse(
                eq("app-123"),
                eq("interaction-token"),
                eq(new DiscordApiClient.EditMessageRequest("response text")),
                eq("Bot bot-token"));
    }

    @Test
    void send_apiThrows_completesWithoutPropagating() {
        doThrow(new RuntimeException("api error")).when(apiClient)
                .editOriginalResponse(any(), any(), any(), any());

        assertDoesNotThrow(() -> adapter.send("token", "text").await().indefinitely());
    }
}
