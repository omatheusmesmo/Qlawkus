package dev.omatheusmesmo.qlawkus.messaging.transcription;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WhisperLanguagesTest {

    @Test
    void mapsTheFullNameWhisperReportsToAnIsoCode() {
        assertEquals(Optional.of("pt"), WhisperLanguages.isoCode("portuguese"));
        assertEquals(Optional.of("en"), WhisperLanguages.isoCode("English"));
        assertEquals(Optional.of("zh"), WhisperLanguages.isoCode("chinese"));
    }

    @Test
    void keepsAnAlreadyIsoCodeAndIgnoresUnknownNames() {
        assertEquals(Optional.of("es"), WhisperLanguages.isoCode("ES"));
        assertEquals(Optional.empty(), WhisperLanguages.isoCode("klingon"));
        assertEquals(Optional.empty(), WhisperLanguages.isoCode(null));
    }

    @Test
    void normalizesAHintToItsPrimaryIsoSubtag() {
        assertEquals(Optional.of("pt"), WhisperLanguages.hint("pt-BR"));
        assertEquals(Optional.of("en"), WhisperLanguages.hint(" EN "));
        assertEquals(Optional.empty(), WhisperLanguages.hint(""));
        assertEquals(Optional.empty(), WhisperLanguages.hint(null));
    }
}
