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

    /**
     * Transcribes audio and reports the language the engine heard, with an optional language hint. The
     * default ignores the hint and reports no language, so an engine without language support keeps working.
     */
    default Transcription transcribe(byte[] audioBytes, String mimeType, String languageHint) {
        return new Transcription(transcribe(audioBytes, mimeType), null);
    }

    /** Name of the speech-to-text engine, logged with each transcription. */
    default String engine() {
        return "unknown";
    }

    /**
     * A transcript and the ISO 639-1 code of the language it was spoken in, or null when the engine did not
     * report one.
     */
    record Transcription(String text, String language) {
    }
}
