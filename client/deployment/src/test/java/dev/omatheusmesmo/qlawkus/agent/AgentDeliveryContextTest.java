package dev.omatheusmesmo.qlawkus.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentDeliveryContextTest {

    private final AgentDeliveryContext context = new AgentDeliveryContext();

    @AfterEach
    void tearDown() {
        context.clear();
    }

    @Test
    void hasDeliveryInfo_defaultIsFalse() {
        assertFalse(context.hasDeliveryInfo());
    }

    @Test
    void set_populatesAllFields() {
        context.set("mem-1", "telegram", "chat-1");

        assertEquals("mem-1", context.memoryId());
        assertEquals("telegram", context.providerId());
        assertEquals("chat-1", context.chatId());
        assertTrue(context.hasDeliveryInfo());
    }

    @Test
    void clear_resetsAllFields() {
        context.set("mem-1", "telegram", "chat-1");
        assertTrue(context.hasDeliveryInfo());

        context.clear();

        assertNull(context.memoryId());
        assertNull(context.providerId());
        assertNull(context.chatId());
        assertFalse(context.hasDeliveryInfo());
    }

    @Test
    void hasDeliveryInfo_missingProviderId_returnsFalse() {
        context.set("mem-1", null, "chat-1");
        assertFalse(context.hasDeliveryInfo());
    }

    @Test
    void hasDeliveryInfo_missingChatId_returnsFalse() {
        context.set("mem-1", "telegram", null);
        assertFalse(context.hasDeliveryInfo());
    }

    @Test
    void set_overwritesPreviousValues() {
        context.set("mem-1", "telegram", "chat-1");
        context.set("mem-2", "discord", "chat-2");

        assertEquals("mem-2", context.memoryId());
        assertEquals("discord", context.providerId());
        assertEquals("chat-2", context.chatId());
    }

    @Test
    void set_threadIsolation() throws Exception {
        context.set("mem-1", "telegram", "chat-1");

        Thread other = new Thread(() -> {
            assertFalse(context.hasDeliveryInfo());
            context.set("mem-2", "discord", "chat-2");
            assertEquals("discord", context.providerId());
        });

        other.start();
        other.join();

        assertEquals("telegram", context.providerId());
    }
}
