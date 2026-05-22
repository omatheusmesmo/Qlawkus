package dev.omatheusmesmo.qlawkus.messaging.tts;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Map;
import java.util.Optional;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.messaging.tts")
public interface TtsConfig {

    /**
     * Whether text-to-speech responses are enabled. When false, the agent's
     * request to respond with voice is ignored and a text reply is sent instead.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Language used when the agent does not specify one, or when no provider
     * matches the requested language.
     */
    @WithDefault("en")
    String defaultLanguage();

    /**
     * TTS providers keyed by language code (e.g. "pt", "en"). Each language can
     * point to a different OpenAI-compatible speech endpoint.
     */
    Map<String, TtsProvider> providers();

    interface TtsProvider {

        /** Provider protocol: "openai" (OpenAI-compatible /v1/audio/speech) or "elevenlabs". */
        @WithDefault("openai")
        String kind();

        /** Base URL of the speech API (without the protocol-specific path). */
        String baseUrl();

        /** Bearer token for the provider. Absent for local servers that need no auth. */
        Optional<String> apiKey();

        /** Model identifier, e.g. "orpheus-v1-english" or a local Piper voice model. */
        String model();

        /** Voice identifier supported by the provider/model. */
        String voice();

        /** Audio response format requested from the provider. */
        @WithDefault("mp3")
        String responseFormat();
    }
}
