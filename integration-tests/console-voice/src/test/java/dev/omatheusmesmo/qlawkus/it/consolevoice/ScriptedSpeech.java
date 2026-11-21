package dev.omatheusmesmo.qlawkus.it.consolevoice;

import dev.omatheusmesmo.qlawkus.console.voice.VoiceSpeech;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Fake speech adapter. The audio it returns is the sentence itself, so a test can tell which sentence a
 * segment carries. It offers English, Portuguese and Spanish, as a distribution with those providers would. A sentence starting with "Slow" blocks until {@link #slowGate} opens, which lets a test
 * hold the worker mid-synthesis and interrupt around it.
 */
@Alternative
@Priority(1000)
@ApplicationScoped
public class ScriptedSpeech implements VoiceSpeech {

    static volatile CountDownLatch slowGate;

    @Override
    public Speech synthesize(String text, String language) {
        CountDownLatch gate = slowGate;
        if (gate != null && text.startsWith("Slow")) {
            try {
                gate.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while held", e);
            }
        }
        return new Speech("audio/mpeg", ("speech:" + text).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String engine() {
        return "scripted";
    }

    @Override
    public boolean buffered() {
        return true;
    }

    @Override
    public List<String> languages() {
        return List.of("en", "pt", "es");
    }

    @Override
    public String defaultLanguage() {
        return "en";
    }
}
