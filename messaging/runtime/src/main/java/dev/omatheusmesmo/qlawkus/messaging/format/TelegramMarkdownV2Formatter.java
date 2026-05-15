package dev.omatheusmesmo.qlawkus.messaging.format;

import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TelegramMarkdownV2Formatter implements MessageFormatter {

    private static final String SPECIAL_CHARS = "_*[]()~`>#+-=|{}.!";

    @Override
    public MessagingFormat format() {
        return MessagingFormat.TELEGRAM_MARKDOWN_V2;
    }

    @Override
    public String format(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length() * 2);
        for (char c : text.toCharArray()) {
            if (SPECIAL_CHARS.indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
