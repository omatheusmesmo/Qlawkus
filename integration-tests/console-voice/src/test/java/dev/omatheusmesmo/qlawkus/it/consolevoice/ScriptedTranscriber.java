package dev.omatheusmesmo.qlawkus.it.consolevoice;

import dev.omatheusmesmo.qlawkus.messaging.transcription.VoiceTranscriptionService;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Fake speech-to-text. Each transcription returns the next scripted line, then the fallback. It records
 * the MIME type each utterance was sent with and the size of the audio, never the audio itself.
 */
@Alternative
@Priority(1000)
@ApplicationScoped
public class ScriptedTranscriber implements VoiceTranscriptionService {

    static final Queue<String> SCRIPT = new ConcurrentLinkedQueue<>();
    static final Queue<String> MIME_TYPES = new ConcurrentLinkedQueue<>();
    static final String FALLBACK = "hello agent";

    @Override
    public String transcribe(byte[] audioBytes) {
        return transcribe(audioBytes, "audio/ogg");
    }

    @Override
    public String transcribe(byte[] audioBytes, String mimeType) {
        MIME_TYPES.add(mimeType);
        String next = SCRIPT.poll();
        return next != null ? next : FALLBACK;
    }

    @Override
    public String engine() {
        return "scripted";
    }

    static List<String> mimeTypes() {
        return List.copyOf(MIME_TYPES);
    }
}
