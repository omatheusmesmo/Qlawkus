package dev.omatheusmesmo.qlawkus.console.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReplyLanguageDetectorTest {

    private static final List<String> LATIN = List.of("en", "pt", "es", "fr", "de", "it");

    @Test
    void recognisesPortugueseFromItsStopwords() {
        assertEquals(Optional.of("pt"), ReplyLanguageDetector.detect(
                "Claro, eu não sei, mas posso ajudar você com isso.", LATIN));
    }

    @Test
    void recognisesEnglishFromItsStopwords() {
        assertEquals(Optional.of("en"), ReplyLanguageDetector.detect(
                "The weather is fine and you can see it with the window open.", LATIN));
    }

    @Test
    void recognisesSpanishFromItsStopwords() {
        assertEquals(Optional.of("es"), ReplyLanguageDetector.detect(
                "Gracias, pero no sé si es para usted, ¿quieres algo más?", LATIN));
    }

    @Test
    void recognisesAScriptThatHasItsOwnAlphabet() {
        assertEquals(Optional.of("ru"), ReplyLanguageDetector.detect("Привет, как дела сегодня?",
                List.of("en", "pt", "ru")));
    }

    @Test
    void neverReturnsALanguageOutsideTheCandidates() {
        assertEquals(Optional.empty(), ReplyLanguageDetector.detect("Привет, как дела сегодня?",
                List.of("en", "pt")));
        assertEquals(Optional.empty(), ReplyLanguageDetector.detect(
                "Claro, eu não sei, mas posso ajudar você com isso.", List.of("en")));
    }

    @Test
    void aReplyWithTooLittleEvidenceIsLeftToTheFallback() {
        assertEquals(Optional.empty(), ReplyLanguageDetector.detect("Sure.", LATIN));
        assertEquals(Optional.empty(), ReplyLanguageDetector.detect("Obrigado.", LATIN));
        assertEquals(Optional.empty(), ReplyLanguageDetector.detect("", LATIN));
    }

    @Test
    void aTieIsNotGuessed() {
        assertTrue(ReplyLanguageDetector.detect("the and não também", LATIN).isEmpty());
    }
}
