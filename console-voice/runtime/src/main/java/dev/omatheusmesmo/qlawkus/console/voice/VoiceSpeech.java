package dev.omatheusmesmo.qlawkus.console.voice;

/**
 * Turns one reply sentence into audio for the live session. The default implementation wraps the
 * messaging text-to-speech router, which is buffered: each sentence is synthesized as a whole, so a
 * sentence waits for its full synthesis before any of it plays. A streaming adapter can replace it
 * with {@code buffered()} returning false, and the page will say so.
 */
public interface VoiceSpeech {

    Speech synthesize(String text);

    String engine();

    boolean buffered();

    record Speech(String mimeType, byte[] audio) {
    }
}
