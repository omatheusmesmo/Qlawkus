package dev.omatheusmesmo.qlawkus.messaging.tts;

import java.util.Optional;

final class TtsProviders {

    private TtsProviders() {
    }

    static TtsConfig.TtsProvider provider(String kind, String baseUrl, String apiKey, String model, String voice,
                                          String responseFormat) {
        return new TtsConfig.TtsProvider() {
            @Override public String kind() { return kind; }
            @Override public String baseUrl() { return baseUrl; }
            @Override public Optional<String> apiKey() { return Optional.ofNullable(apiKey); }
            @Override public String model() { return model; }
            @Override public String voice() { return voice; }
            @Override public String responseFormat() { return responseFormat; }
            @Override public Optional<String> fallback() { return Optional.empty(); }
        };
    }
}
