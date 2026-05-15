package dev.omatheusmesmo.qlawkus.messaging.format;

import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SlackMarkdownFormatter implements MessageFormatter {

    @Override
    public MessagingFormat format() {
        return MessagingFormat.SLACK_MARKDOWN;
    }

    @Override
    public String format(String text) {
        if (text == null) return "";
        return text
                .replaceAll("\\*\\*(.+?)\\*\\*", "*$1*")
                .replaceAll("__(.+?)__", "_$1_");
    }
}
