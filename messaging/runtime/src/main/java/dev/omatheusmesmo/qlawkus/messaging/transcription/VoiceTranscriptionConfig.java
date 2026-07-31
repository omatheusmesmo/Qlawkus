package dev.omatheusmesmo.qlawkus.messaging.transcription;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.messaging.transcription")
public interface VoiceTranscriptionConfig {

    /**
     * OpenAI API key for Whisper audio transcription. Optional: with no key the agent boots normally
     * and transcription stays off, so voice is something a deployment adds rather than something it
     * must supply before starting.
     */
    Optional<String> apiKey();

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
