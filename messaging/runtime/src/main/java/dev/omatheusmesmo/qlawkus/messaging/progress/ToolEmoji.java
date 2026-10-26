package dev.omatheusmesmo.qlawkus.messaging.progress;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public final class ToolEmoji {

    public static final String DEFAULT = "🔧";

    private static final List<Category> CATEGORIES = List.of(
            new Category("📊", ToolEmoji::isTelemetry),
            new Category("🧠", name -> containsAny(name, "memor", "remember", "fact", "recall", "profile",
                    "journal", "transcript", "skill", "soul", "clear")),
            new Category("📅", name -> containsAny(name, "calendar", "event", "meeting")),
            new Category("📧", name -> containsAny(name, "mail", "inbox", "draft")),
            new Category("💻", name -> containsAny(name, "shell", "exec", "command", "terminal", "process", "pty")),
            new Category("🔎", name -> containsAny(name, "search", "web", "fetch", "browse", "lookup", "grep")),
            new Category("📄", name -> containsAny(name, "file", "drive", "doc", "sheet", "slide", "read", "write",
                    "list", "folder", "directory"))
    );

    private ToolEmoji() {
    }

    public static String of(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return DEFAULT;
        }
        String name = toolName.toLowerCase(Locale.ROOT);
        for (Category category : CATEGORIES) {
            if (category.matches().test(name)) {
                return category.emoji();
            }
        }
        return DEFAULT;
    }

    private static boolean isTelemetry(String name) {
        return containsAny(name, "metric", "trace", "telemetry", "querylogs");
    }

    private static boolean containsAny(String name, String... fragments) {
        for (String fragment : fragments) {
            if (name.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private record Category(String emoji, Predicate<String> matches) {
    }
}
