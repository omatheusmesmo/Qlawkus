package dev.omatheusmesmo.qlawkus.messaging.tts;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class ElevenLabsTtsClient implements TtsClient {

    private final ElevenLabsSpeechApi api;

    @Inject
    public ElevenLabsTtsClient(@RestClient ElevenLabsSpeechApi api) {
        this.api = api;
    }

    @Override
    public String kind() {
        return "elevenlabs";
    }

    @Override
    public byte[] synthesize(TtsConfig.TtsProvider provider, String text) {
        String apiKey = provider.apiKey()
                .filter(key -> !key.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "ElevenLabs provider requires an api-key"));

        return api.speech(provider.baseUrl(), provider.voice(), outputFormat(provider.responseFormat()), apiKey,
                new ElevenLabsSpeechApi.SpeechRequest(text, provider.model()));
    }

    private String outputFormat(String responseFormat) {
        return "mp3".equalsIgnoreCase(responseFormat) ? "mp3_44100_128" : responseFormat;
    }
}
