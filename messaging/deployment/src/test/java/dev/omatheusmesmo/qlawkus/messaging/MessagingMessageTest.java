package dev.omatheusmesmo.qlawkus.messaging;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MessagingMessageTest {

    @Test
    void textFactory_createsMessageWithEmptyAudio() {
        MessagingMessage msg = MessagingMessage.text("telegram", "chat-1", "user-1", "hello");

        assertEquals("telegram", msg.providerId());
        assertEquals("chat-1", msg.chatId());
        assertEquals("user-1", msg.userId());
        assertEquals("hello", msg.text());
        assertTrue(msg.audio().isEmpty());
    }

    @Test
    void fullConstructor_withAudio_presentsPayload() {
        byte[] audio = new byte[]{1, 2, 3};
        MessagingMessage msg = new MessagingMessage("telegram", "chat-1", "user-1", null, Optional.of(audio));

        assertTrue(msg.audio().isPresent());
        assertArrayEquals(audio, msg.audio().get());
    }

    @Test
    void messagesWithSameFields_areEqual() {
        MessagingMessage a = MessagingMessage.text("telegram", "c", "u", "hi");
        MessagingMessage b = MessagingMessage.text("telegram", "c", "u", "hi");

        assertEquals(a, b);
    }
}
