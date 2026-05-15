package dev.omatheusmesmo.qlawkus.messaging.transcription;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.messaging.transcription")
public interface VoiceTranscriptionConfig {

    /**
     * OpenAI API key for Whisper audio transcription. Leave empty to disable transcription.
     */
    @WithDefault("")
    String apiKey();

    /**
     * Whisper model to use for transcription.
     */
    @WithDefault("whisper-1")
    String model();

    /**
     * Base URL of the transcription API.
     */
    @WithDefault("https://api.openai.com")
    String baseUrl();
}
