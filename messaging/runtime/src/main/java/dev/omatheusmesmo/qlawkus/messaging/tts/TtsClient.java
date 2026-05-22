package dev.omatheusmesmo.qlawkus.messaging.tts;

public interface TtsClient {

    /** Provider protocol this client handles, matched against {@code TtsProvider.kind()}. */
    String kind();

    /**
     * Synthesizes speech for the given text using the supplied provider.
     *
     * @return the encoded audio bytes (format defined by the provider's responseFormat)
     */
    byte[] synthesize(TtsConfig.TtsProvider provider, String text);
}
