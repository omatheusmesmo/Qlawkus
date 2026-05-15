package dev.omatheusmesmo.qlawkus.messaging;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkingServiceTest {

    private final ChunkingService service = new ChunkingService();

    @Test
    void chunk_shortText_returnsSingleChunk() {
        List<String> result = service.chunk("hello world", "telegram");
        assertEquals(1, result.size());
        assertEquals("hello world", result.get(0));
    }

    @Test
    void chunk_nullText_returnsEmptyList() {
        assertTrue(service.chunk(null, "telegram").isEmpty());
    }

    @Test
    void chunk_blankText_returnsEmptyList() {
        assertTrue(service.chunk("   ", "telegram").isEmpty());
    }

    @Test
    void chunk_textExceedsTelegramLimit_splits() {
        String text = "a".repeat(5000);
        List<String> chunks = service.chunk(text, "telegram");
        assertTrue(chunks.size() > 1);
        chunks.forEach(c -> assertTrue(c.length() <= 4096,
                "chunk length " + c.length() + " exceeds Telegram limit"));
    }

    @Test
    void chunk_textExceedsDiscordLimit_splits() {
        String text = "word ".repeat(500);
        List<String> chunks = service.chunk(text, "discord");
        chunks.forEach(c -> assertTrue(c.length() <= 2000,
                "chunk length " + c.length() + " exceeds Discord limit"));
    }

    @Test
    void chunk_whatsAppAndSlack_haveHigherLimits() {
        String text = "x".repeat(10000);
        assertEquals(1, service.chunk(text, "whatsapp").size());
        assertEquals(1, service.chunk(text, "slack").size());
    }

    @Test
    void chunk_unknownProvider_usesDefaultLimit() {
        String text = "a".repeat(5000);
        List<String> chunks = service.chunk(text, "unknown");
        chunks.forEach(c -> assertTrue(c.length() <= ChunkingService.DEFAULT_LIMIT));
    }

    @Test
    void chunk_splitsPreferParagraphBreaks() {
        String text = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph.";
        List<String> chunks = service.chunk(text, "telegram");
        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0));
    }

    @Test
    void chunk_preservesTextContent() {
        String original = "Hello world. ".repeat(400).trim();
        List<String> chunks = service.chunk(original, "discord");
        String reassembled = String.join(" ", chunks);
        assertEquals(original.replaceAll("\\s+", " "), reassembled.replaceAll("\\s+", " "));
    }
}
