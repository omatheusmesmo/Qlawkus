package dev.omatheusmesmo.qlawkus.messaging;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ChunkingService {

    static final Map<String, Integer> PROVIDER_LIMITS = Map.of(
            "telegram", 4096,
            "discord", 2000,
            "whatsapp", 65536,
            "slack", 40000
    );

    static final int DEFAULT_LIMIT = 4096;

    public List<String> chunk(String text, String providerId) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int limit = PROVIDER_LIMITS.getOrDefault(providerId, DEFAULT_LIMIT);
        return splitByLimit(text, limit);
    }

    private List<String> splitByLimit(String text, int limit) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + limit, text.length());
            if (end < text.length()) {
                int boundary = findSplitBoundary(text, start, end);
                chunks.add(text.substring(start, boundary).stripTrailing());
                start = boundary;
                while (start < text.length() && text.charAt(start) == '\n') {
                    start++;
                }
            } else {
                chunks.add(text.substring(start));
                start = end;
            }
        }
        return chunks;
    }

    private int findSplitBoundary(String text, int start, int end) {
        int paragraphBreak = text.lastIndexOf("\n\n", end);
        if (paragraphBreak > start) return paragraphBreak;

        int lineBreak = text.lastIndexOf('\n', end);
        if (lineBreak > start) return lineBreak;

        int spaceBreak = text.lastIndexOf(' ', end);
        if (spaceBreak > start) return spaceBreak;

        return end;
    }
}
