package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.messaging.format.FormatterRegistry;
import dev.omatheusmesmo.qlawkus.messaging.format.MessageFormatter;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationService service;
    private ProviderRegistry providerRegistry;
    private FormatterRegistry formatterRegistry;
    private ChunkingService chunkingService;
    private MessagingProvider provider;

    @BeforeEach
    void setUp() {
        service = new NotificationService();
        providerRegistry = Mockito.mock(ProviderRegistry.class);
        formatterRegistry = Mockito.mock(FormatterRegistry.class);
        chunkingService = Mockito.mock(ChunkingService.class);
        provider = Mockito.mock(MessagingProvider.class);

        service.providerRegistry = providerRegistry;
        service.formatterRegistry = formatterRegistry;
        service.chunkingService = chunkingService;

        when(provider.providerId()).thenReturn("telegram");
        when(provider.supportedFormat()).thenReturn(MessagingFormat.TELEGRAM_MARKDOWN_V2);
        when(provider.send(any(), any())).thenReturn(Uni.createFrom().voidItem());
        MessageFormatter passThrough = new MessageFormatter() {
            @Override public MessagingFormat format() { return MessagingFormat.PLAIN_TEXT; }
            @Override public String format(String text) { return text == null ? "" : text; }
        };
        when(formatterRegistry.forFormat(any())).thenReturn(passThrough);
    }

    @Test
    void send_knownProvider_delegatesToProviderSend() {
        when(providerRegistry.getProvider("telegram")).thenReturn(Optional.of(provider));
        when(chunkingService.chunk(any(), eq("telegram"))).thenReturn(List.of("hello"));

        service.send("telegram", "chat-1", "hello").await().indefinitely();

        verify(provider).send("chat-1", "hello");
    }

    @Test
    void send_unknownProvider_completesWithoutError() {
        when(providerRegistry.getProvider("unknown")).thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
                service.send("unknown", "chat-1", "hello").await().indefinitely());
        verify(provider, never()).send(any(), any());
    }

    @Test
    void send_longText_sendsAllChunks() {
        when(providerRegistry.getProvider("telegram")).thenReturn(Optional.of(provider));
        when(chunkingService.chunk(any(), eq("telegram"))).thenReturn(List.of("chunk1", "chunk2", "chunk3"));

        service.send("telegram", "chat-1", "long text").await().indefinitely();

        verify(provider).send("chat-1", "chunk1");
        verify(provider).send("chat-1", "chunk2");
        verify(provider).send("chat-1", "chunk3");
    }

    @Test
    void broadcast_sendsToAllActiveProviders() {
        MessagingProvider discord = Mockito.mock(MessagingProvider.class);
        when(discord.providerId()).thenReturn("discord");
        when(discord.supportedFormat()).thenReturn(MessagingFormat.DISCORD_MARKDOWN);
        when(discord.send(any(), any())).thenReturn(Uni.createFrom().voidItem());

        when(providerRegistry.activeProviders()).thenReturn(List.of(provider, discord));
        when(providerRegistry.getProvider("telegram")).thenReturn(Optional.of(provider));
        when(providerRegistry.getProvider("discord")).thenReturn(Optional.of(discord));
        when(chunkingService.chunk(any(), any())).thenReturn(List.of("hi"));

        service.broadcast("hi").await().indefinitely();

        verify(provider).send(null, "hi");
        verify(discord).send(null, "hi");
    }
}
