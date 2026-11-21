package dev.omatheusmesmo.qlawkus.messaging.tts;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class HttpTtsClient implements TtsClient {

    private final OpenAiSpeechApi api;

    @Inject
    public HttpTtsClient(@RestClient OpenAiSpeechApi api) {
        this.api = api;
    }

    @Override
    public String kind() {
        return "openai";
    }

    @Override
    public byte[] synthesize(TtsConfig.TtsProvider provider, String text) {
        String authorization = provider.apiKey()
                .filter(key -> !key.isBlank())
                .map(key -> "Bearer " + key)
                .orElse(null);
        return api.speech(provider.baseUrl(), authorization, new OpenAiSpeechApi.SpeechRequest(
                provider.model(), text, provider.voice(), provider.responseFormat()));
    }
}
