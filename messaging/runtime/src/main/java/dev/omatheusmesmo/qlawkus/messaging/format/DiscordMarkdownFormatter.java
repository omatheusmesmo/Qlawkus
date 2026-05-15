package dev.omatheusmesmo.qlawkus.messaging.format;

import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DiscordMarkdownFormatter implements MessageFormatter {

    @Override
    public MessagingFormat format() {
        return MessagingFormat.DISCORD_MARKDOWN;
    }

    @Override
    public String format(String text) {
        if (text == null) return "";
        return text;
    }
}
