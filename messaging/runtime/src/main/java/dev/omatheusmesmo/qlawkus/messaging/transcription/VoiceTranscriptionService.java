package dev.omatheusmesmo.qlawkus.messaging.transcription;

public interface VoiceTranscriptionService {

    String transcribe(byte[] audioBytes);
}
