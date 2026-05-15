package dev.omatheusmesmo.qlawkus.messaging.format;

import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageFormatterTest {

    @Test
    void telegramFormatter_escapesSpecialCharacters() {
        TelegramMarkdownV2Formatter f = new TelegramMarkdownV2Formatter();
        assertEquals("hello\\.", f.format("hello."));
        assertEquals("\\*bold\\*", f.format("*bold*"));
        assertEquals("\\[link\\]", f.format("[link]"));
    }

    @Test
    void telegramFormatter_nullInput_returnsEmpty() {
        assertEquals("", new TelegramMarkdownV2Formatter().format(null));
    }

    @Test
    void telegramFormatter_format_isTelegramMarkdownV2() {
        assertEquals(MessagingFormat.TELEGRAM_MARKDOWN_V2, new TelegramMarkdownV2Formatter().format());
    }

    @Test
    void discordFormatter_passesTextThrough() {
        DiscordMarkdownFormatter f = new DiscordMarkdownFormatter();
        assertEquals("**bold** and _italic_", f.format("**bold** and _italic_"));
    }

    @Test
    void discordFormatter_nullInput_returnsEmpty() {
        assertEquals("", new DiscordMarkdownFormatter().format(null));
    }

    @Test
    void slackFormatter_convertsBoldSyntax() {
        SlackMarkdownFormatter f = new SlackMarkdownFormatter();
        assertEquals("*bold* text", f.format("**bold** text"));
    }

    @Test
    void slackFormatter_convertsItalicSyntax() {
        SlackMarkdownFormatter f = new SlackMarkdownFormatter();
        assertEquals("_italic_", f.format("__italic__"));
    }

    @Test
    void whatsAppFormatter_keepsBoldAndItalic() {
        WhatsAppHtmlFormatter f = new WhatsAppHtmlFormatter();
        assertEquals("*bold*", f.format("**bold**"));
        assertEquals("_italic_", f.format("__italic__"));
    }

    @Test
    void whatsAppFormatter_nullInput_returnsEmpty() {
        assertEquals("", new WhatsAppHtmlFormatter().format(null));
    }
}
