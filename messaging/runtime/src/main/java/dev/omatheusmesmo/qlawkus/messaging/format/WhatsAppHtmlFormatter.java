package dev.omatheusmesmo.qlawkus.messaging.format;

import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WhatsAppHtmlFormatter implements MessageFormatter {

    @Override
    public MessagingFormat format() {
        return MessagingFormat.WHATSAPP_HTML;
    }

    @Override
    public String format(String text) {
        if (text == null) return "";
        return text
                .replaceAll("\\*\\*(.+?)\\*\\*", "*$1*")
                .replaceAll("__(.+?)__", "_$1_")
                .replaceAll("`{3}([^`]+)`{3}", "```$1```");
    }
}
