package dev.omatheusmesmo.qlawkus.it.consolevoice;

import dev.omatheusmesmo.qlawkus.messaging.transcription.VoiceTranscriptionService;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fake speech-to-text. Each transcription returns the next scripted line, with the language the script gave
 * it, then the fallback. It records the MIME type and the language hint of each call, never the audio itself.
 */
@Alternative
@Priority(1000)
@ApplicationScoped
public class ScriptedTranscriber implements VoiceTranscriptionService {

    static final Queue<Heard> SCRIPT = new ConcurrentLinkedQueue<>();
    static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    static final String FALLBACK = "hello agent";

    record Heard(String text, String language) {
    }

    record Call(String mimeType, String hint) {
    }

    static void say(String text) {
        SCRIPT.add(new Heard(text, null));
    }

    static void say(String text, String language) {
        SCRIPT.add(new Heard(text, language));
    }

    static List<String> mimeTypes() {
        return CALLS.stream().map(Call::mimeType).toList();
    }

    @Override
    public String transcribe(byte[] audioBytes) {
        return transcribe(audioBytes, "audio/ogg");
    }

    @Override
    public String transcribe(byte[] audioBytes, String mimeType) {
        CALLS.add(new Call(mimeType, null));
        Heard next = SCRIPT.poll();
        return next != null ? next.text() : FALLBACK;
    }

    @Override
    public Transcription transcribe(byte[] audioBytes, String mimeType, String languageHint) {
        CALLS.add(new Call(mimeType, languageHint));
        Heard next = SCRIPT.poll();
        return next != null
                ? new Transcription(next.text(), next.language())
                : new Transcription(FALLBACK, null);
    }

    @Override
    public String engine() {
        return "scripted";
    }
}
