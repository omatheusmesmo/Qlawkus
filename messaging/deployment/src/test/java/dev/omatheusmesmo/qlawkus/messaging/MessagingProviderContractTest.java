package dev.omatheusmesmo.qlawkus.messaging;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessagingProviderContractTest {

    private static final class StubProvider implements MessagingProvider {
        @Override
        public String providerId() { return "stub"; }

        @Override
        public MessagingFormat supportedFormat() { return MessagingFormat.PLAIN_TEXT; }

        @Override
        public Uni<MessagingResponse> receive(MessagingMessage message) {
            return Uni.createFrom().item(new MessagingResponse(message.chatId(), "echo: " + message.text()));
        }

        @Override
        public Uni<Void> send(String chatId, String text) {
            return Uni.createFrom().voidItem();
        }
    }

    @Test
    void providerId_isStable() {
        MessagingProvider provider = new StubProvider();
        assertEquals("stub", provider.providerId());
    }

    @Test
    void supportedFormat_isNotNull() {
        assertNotNull(new StubProvider().supportedFormat());
    }

    @Test
    void receive_returnsResponseWithSameChatId() {
        MessagingMessage msg = MessagingMessage.text("stub", "chat-42", "user-1", "hello");
        MessagingResponse response = new StubProvider().receive(msg).await().indefinitely();

        assertEquals("chat-42", response.chatId());
        assertFalse(response.text().isBlank());
    }

    @Test
    void send_completesWithoutError() {
        assertDoesNotThrow(() ->
                new StubProvider().send("chat-1", "ping").await().indefinitely());
    }

    @Test
    void messagingResponse_isRecord() {
        MessagingResponse r = new MessagingResponse("chat-1", "text");
        assertEquals("chat-1", r.chatId());
        assertEquals("text", r.text());
    }
}
