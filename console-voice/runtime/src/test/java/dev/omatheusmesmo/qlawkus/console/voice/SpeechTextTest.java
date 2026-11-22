package dev.omatheusmesmo.qlawkus.console.voice;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SpeechTextTest {
    @Test
    void rendersFormattingAndLinkLabelsWithoutPresentationOrDestinations() {
        assertEquals("Resumo. Primeiro item e segundo. Consulte a documentação e use git status.",
                clean("# Resumo\n\n- **Primeiro** item e _segundo_.\n\nConsulte [a documentação](https://example.org) e use `git status`."));
    }

    @Test
    void dropsCodeTablesImagesAndHtmlWhileKeepingOtherProse() {
        assertEquals("Antes. Depois.", clean("""
                Antes.

                ```java
                System.out.println("do not speak");

                otherCode();
                ```

                | A | B |
                |---|---|
                | 1 | 2 |

                ![do not speak image](image.png)

                <script>doNotSpeak();</script>

                Depois.
                """));
    }

    @Test
    void handlesFencesAndInlineFormattingSplitAcrossEveryModelChunk() {
        SpeechText speech = new SpeechText();
        String source = "**Bom dia. Tudo bem?**\n\n```java\nsecret();\n\nother();\n```\n\nAté logo.";
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < source.length(); i++) output.append(speech.accept(source.substring(i, i + 1))).append(' ');
        output.append(speech.flush());
        assertEquals("Bom dia. Tudo bem? Até logo.", output.toString().replaceAll("\\s+", " ").trim());
    }

    @Test
    void completeParagraphsCanSpeakBeforeTheEndOfTheTurn() {
        SpeechText speech = new SpeechText();
        assertEquals("", speech.accept("Olá **Matheus"));
        assertEquals("Olá Matheus.", speech.accept("**.\n\n"));
        assertEquals("", speech.accept("Próximo parágrafo."));
        assertEquals("Próximo parágrafo.", speech.flush());
    }

    @Test
    void stripsEmojiSequencesWithoutLosingPortugueseNumbersOrUnits() {
        assertEquals("Ação 123 a 25°C e €5. Certo!", clean("Ação 123 a 25°C e €5. 👨‍👩‍👧‍👦 🇧🇷 👍🏽 Certo!"));
        assertEquals("item", clean("1️⃣ item"));
        assertEquals("Resumo. Tudo bem?", clean("# Resumo 1️⃣\n\n- Tudo bem? 🔧\n- 👍"));
        assertEquals("continua", clean("~~continua~~"));
    }

    @Test
    void boundsTheVoiceProseInput() {
        SpeechText speech = new SpeechText();
        speech.accept("a".repeat(SpeechText.MAX_SOURCE_CHARS + 20));
        assertTrue(speech.truncated());
        assertEquals(SpeechText.MAX_SOURCE_CHARS, speech.flush().length());
        assertEquals("", speech.accept("not spoken"));
        assertEquals("", speech.flush());
    }

    @Test
    void unclosedCodeIsNotNarratedAndCrLfParagraphsWork() {
        assertEquals("Explicação.", clean("Explicação.\r\n\r\n```java\r\nneverSpeak();"));
        SpeechText speech = new SpeechText();
        assertEquals("Olá.", speech.accept("Olá.\r\n \r\n"));
    }

    private static String clean(String markdown) {
        SpeechText speech = new SpeechText();
        String first = speech.accept(markdown);
        String rest = speech.flush();
        return (first + " " + rest).trim();
    }
}
