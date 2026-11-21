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

    @Test
    void inAutoModeTheReplyFollowsItsOwnTextFirst() {
        Optional<String> stable = Optional.of("en");

        assertEquals("pt", languages.replyLanguage(VoiceLanguages.AUTO, stable,
                "Claro, eu não sei, mas posso ajudar você com isso."));
    }

    @Test
    void inAutoModeAnUninformativeReplyFallsBackToTheSessionLanguageThenTheDefault() {
        assertEquals("pt", languages.replyLanguage(VoiceLanguages.AUTO, Optional.of("pt"), "Ok."));
        assertEquals("en", languages.replyLanguage(VoiceLanguages.AUTO, Optional.empty(), "Ok."));
    }

    @Test
    void aSessionLanguageWithoutAProviderIsNotUsedForSpeech() {
        assertEquals("en", languages.replyLanguage(VoiceLanguages.AUTO, Optional.of("ja"), "Ok."));
    }

    @Test
    void anExplicitChoiceForcesTheReplyLanguageWhateverTheTextSays() {
        assertEquals("es", languages.replyLanguage("es", Optional.of("pt"),
                "The weather is fine and you can see it with the window open."));
    }

    @Test
    void anExplicitChoiceWithoutAProviderSpeaksInTheDefaultLanguage() {
        assertEquals("en", languages.replyLanguage("de", Optional.empty(), "Guten Tag."));
    }
}
