package dev.omatheusmesmo.qlawkus.messaging.transcription;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoiceTranscriptionServiceTest {

    @Test
    void aLambdaImplementationReportsUnknownEngine() {
        VoiceTranscriptionService service = audio -> "x";

        assertEquals("unknown", service.engine());
    }
}
