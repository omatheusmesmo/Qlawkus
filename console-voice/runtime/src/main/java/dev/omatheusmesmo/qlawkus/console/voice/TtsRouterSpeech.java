package dev.omatheusmesmo.qlawkus.console.voice;

import dev.omatheusmesmo.qlawkus.messaging.tts.TtsRouter;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Default speech adapter: the messaging text-to-speech router. Providers are the same
 * {@code qlawkus.messaging.tts.providers.*} entries the messaging channels use, so a self-hosted server
 * works here without a paid voice API, and the languages offered to the page are exactly the ones the
 * router has a provider for. The router's responses are mp3, which is what the messaging providers request
 * by default.
 */
@DefaultBean
@ApplicationScoped
public class TtsRouterSpeech implements VoiceSpeech {

    private static final String MP3 = "audio/mpeg";

    private final TtsRouter router;

    @Inject
    public TtsRouterSpeech(TtsRouter router) {
        this.router = router;
    }

    @Override
    public Speech synthesize(String text, String language) {
        return new Speech(MP3, router.synthesize(text, language));
    }

    @Override
    public List<String> languages() {
        return List.copyOf(router.languages());
    }

    @Override
    public String defaultLanguage() {
        return router.defaultLanguage();
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
