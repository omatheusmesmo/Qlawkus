package dev.omatheusmesmo.qlawkus.console.voice;

import java.util.List;

/**
 * Turns one reply sentence into audio for the live session, in the language the session chose for it. The
 * default implementation wraps the messaging text-to-speech router, which is buffered: each sentence is
 * synthesized as a whole, so a sentence waits for its full synthesis before any of it plays. A streaming
 * adapter can replace it with {@code buffered()} returning false, and the page will say so.
 */
public interface VoiceSpeech {

    Speech synthesize(String text, String language);

    /**
     * Language codes this adapter can speak, in the order the page offers them. Empty when it cannot pick a
     * language, in which case every reply uses {@link #defaultLanguage()}.
     */
    List<String> languages();

    String defaultLanguage();

    String engine();

    boolean buffered();

    record Speech(String mimeType, byte[] audio) {
    }
}
