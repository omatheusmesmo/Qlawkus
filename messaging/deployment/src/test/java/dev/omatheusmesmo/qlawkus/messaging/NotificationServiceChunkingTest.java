package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.messaging.format.FormatterRegistry;
import dev.omatheusmesmo.qlawkus.messaging.format.MessageFormatter;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * End-to-end chunking across providers (#86): a long reply must be split within
 * each provider's real message limit and delivered without losing content.
 */
class NotificationServiceChunkingTest {

    private static final Map<String, Integer> PROVIDER_LIMITS = Map.of(
            "telegram", 4096,
            "discord", 2000,
            "whatsapp", 65536,
            "slack", 40000);

    private NotificationService service;
    private ProviderRegistry providerRegistry;

    @BeforeEach
    void setUp() {
        service = new NotificationService();
        providerRegistry = Mockito.mock(ProviderRegistry.class);
        FormatterRegistry formatterRegistry = Mockito.mock(FormatterRegistry.class);

        service.providerRegistry = providerRegistry;
        service.formatterRegistry = formatterRegistry;
        service.chunkingService = new ChunkingService();

        MessageFormatter passThrough = new MessageFormatter() {
            @Override public MessagingFormat format() { return MessagingFormat.PLAIN_TEXT; }
            @Override public String format(String text) { return text == null ? "" : text; }
        };
        when(formatterRegistry.forFormat(any())).thenReturn(passThrough);
    }

    @Test
    void longReply_isChunkedWithinEachProviderLimitAndPreservesContent() {
        for (Map.Entry<String, Integer> entry : PROVIDER_LIMITS.entrySet()) {
            String providerId = entry.getKey();
            int limit = entry.getValue();

            RecordingProvider provider = new RecordingProvider(providerId);
            when(providerRegistry.getProvider(providerId)).thenReturn(Optional.of(provider));

            String original = words(limit * 2 + 500);
            service.send(providerId, "chat-1", original).await().indefinitely();

            assertTrue(provider.sent.size() >= 2, providerId + " should split into multiple chunks");
            for (String chunk : provider.sent) {
                assertTrue(chunk.length() <= limit,
                        providerId + " chunk exceeds limit: " + chunk.length() + " > " + limit);
            }
            String rejoined = String.join(" ", provider.sent).replaceAll("\\s+", " ").trim();
            assertEquals(original.replaceAll("\\s+", " ").trim(), rejoined,
                    providerId + " chunking lost content");
        }
    }

    private String words(int minLength) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (sb.length() < minLength) {
            sb.append("word").append(i++).append(' ');
        }
        return sb.toString().trim();
    }

    private static final class RecordingProvider implements MessagingProvider {
        private final String id;
        final List<String> sent = new ArrayList<>();

        RecordingProvider(String id) {
            this.id = id;
        }

        @Override public String providerId() { return id; }
        @Override public MessagingFormat supportedFormat() { return MessagingFormat.PLAIN_TEXT; }

        @Override
        public Uni<MessagingResponse> receive(MessagingMessage message) {
            return Uni.createFrom().item(new MessagingResponse(message.chatId(), ""));
        }

        @Override
        public Uni<Void> send(String chatId, String text) {
            sent.add(text);
            return Uni.createFrom().voidItem();
        }
    }
}
