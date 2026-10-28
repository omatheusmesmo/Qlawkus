package dev.omatheusmesmo.qlawkus.messaging.transcription;

public interface VoiceTranscriptionService {

    String transcribe(byte[] audioBytes);

    /** Name of the speech-to-text engine, logged with each transcription. */
    default String engine() {
        return "unknown";
    }
}
