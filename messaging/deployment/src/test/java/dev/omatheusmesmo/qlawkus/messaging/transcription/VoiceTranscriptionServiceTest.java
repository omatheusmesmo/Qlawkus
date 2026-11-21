package dev.omatheusmesmo.qlawkus.messaging.transcription;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VoiceTranscriptionServiceTest {

    @Test
    void aLambdaImplementationReportsUnknownEngine() {
        VoiceTranscriptionService service = audio -> "x";

        assertEquals("unknown", service.engine());
    }

    @Test
    void anEngineWithoutLanguageSupportIgnoresTheHintAndReportsNoLanguage() {
        VoiceTranscriptionService service = audio -> "hello";

        VoiceTranscriptionService.Transcription result =
                service.transcribe(new byte[]{1}, "audio/wav", "pt");

        assertEquals("hello", result.text());
        assertNull(result.language());
    }
}
