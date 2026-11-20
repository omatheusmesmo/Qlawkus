package dev.omatheusmesmo.qlawkus.console.voice;

import dev.omatheusmesmo.qlawkus.messaging.tts.TtsRouter;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Default speech adapter: the messaging text-to-speech router with the console's configured language.
 * Providers are the same {@code qlawkus.messaging.tts.providers.*} entries the messaging channels use,
 * so a self-hosted server works here without a paid voice API. The router's responses are mp3, which
 * is what the messaging providers request by default.
 */
@DefaultBean
@ApplicationScoped
public class TtsRouterSpeech implements VoiceSpeech {

    private static final String MP3 = "audio/mpeg";

    private final TtsRouter router;
    private final ConsoleVoiceConfig config;

    @Inject
    public TtsRouterSpeech(TtsRouter router, ConsoleVoiceConfig config) {
        this.router = router;
        this.config = config;
    }

    @Override
    public Speech synthesize(String text) {
        return new Speech(MP3, router.synthesize(text, config.language()));
    }

    @Override
    public String engine() {
        return "tts-router";
    }

    @Override
    public boolean buffered() {
        return true;
    }
}
