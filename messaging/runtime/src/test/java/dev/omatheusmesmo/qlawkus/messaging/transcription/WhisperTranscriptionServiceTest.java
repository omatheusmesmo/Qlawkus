package dev.omatheusmesmo.qlawkus.messaging.transcription;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhisperTranscriptionServiceTest {

    private final WhisperApiClient noRequests = (baseUrl, authorization, form) -> {
        throw new AssertionError("no request expected");
    };

    @Test
    void transcribe_throwsWhenApiKeyIsBlank() {
        WhisperTranscriptionService service = new WhisperTranscriptionService(
            config("", "whisper-1", "https://api.openai.com"), noRequests);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.transcribe(new byte[]{1, 2, 3}));
        assertTrue(ex.getMessage().contains("api-key"));
    }

    @Test
    void transcribeWithLanguageRejectsAHintThatIsNotAnIsoCode() {
        WhisperTranscriptionService service = new WhisperTranscriptionService(
            config("sk-test", "whisper-1", "http://localhost:1"), noRequests);

        assertThrows(IllegalArgumentException.class,
                () -> service.transcribe(new byte[]{1}, "audio/wav", "portuguese language"));
    }

    private VoiceTranscriptionConfig config(String apiKey, String model, String baseUrl) {
        return new VoiceTranscriptionConfig() {
            @Override public Optional<String> apiKey() { return Optional.ofNullable(apiKey); }
            @Override public String model() { return model; }
            @Override public String baseUrl() { return baseUrl; }
        };
    }
}
