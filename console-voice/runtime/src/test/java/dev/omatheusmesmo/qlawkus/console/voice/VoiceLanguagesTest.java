package dev.omatheusmesmo.qlawkus.console.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VoiceLanguagesTest {

    private final VoiceLanguages languages = new VoiceLanguages(List.of("en", "pt", "es"), "en");

    @Test
    void aTagIsReducedToItsPrimaryLowerCaseSubtag() {
        assertEquals(Optional.of("pt"), VoiceLanguages.code("PT-br"));
        assertEquals(Optional.of("en"), VoiceLanguages.code(" en_US "));
        assertEquals(Optional.empty(), VoiceLanguages.code("portuguese"));
        assertEquals(Optional.empty(), VoiceLanguages.code(""));
        assertEquals(Optional.empty(), VoiceLanguages.code(null));
    }

    @Test
    void theChoiceIsAcceptedOnlyWhenItIsAutoOrASpeakableLanguage() {
        assertTrue(languages.accepts(VoiceLanguages.AUTO));
        assertTrue(languages.accepts("pt"));
        assertFalse(languages.accepts("de"));
        assertFalse(languages.accepts(""));
    }

    @Test
    void theOfferedListIsTheSpeakableLanguagesInOrder() {
        assertEquals(List.of("en", "pt", "es"), languages.offered());
    }

    @Test
    void noSttHintIsSentInAutoModeAndTheChosenLanguageOtherwise() {
        assertEquals(Optional.empty(), languages.sttHint(VoiceLanguages.AUTO));
        assertEquals(Optional.of("pt"), languages.sttHint("pt"));
    }

}
