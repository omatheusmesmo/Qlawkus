package dev.omatheusmesmo.qlawkus.messaging.transcription;

public interface VoiceTranscriptionService {

    String transcribe(byte[] audioBytes);

    /**
     * Transcribes audio whose container is named by its MIME type. The default ignores the type and
     * delegates to {@link #transcribe(byte[])}, so an engine that only takes voice notes keeps working.
     */
    default String transcribe(byte[] audioBytes, String mimeType) {
        return transcribe(audioBytes);
    }

    /** Name of the speech-to-text engine, logged with each transcription. */
    default String engine() {
        return "unknown";
    }
}
