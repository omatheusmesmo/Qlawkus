package dev.omatheusmesmo.qlawkus.messaging.transcription;

import io.vertx.core.buffer.Buffer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Locale;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.client.api.ClientMultipartForm;

@ApplicationScoped
public class WhisperTranscriptionService implements VoiceTranscriptionService {

    private static final String VERBOSE_JSON = "verbose_json";

    private final VoiceTranscriptionConfig config;
    private final WhisperApiClient client;

    @Inject
    public WhisperTranscriptionService(VoiceTranscriptionConfig config, @RestClient WhisperApiClient client) {
        this.config = config;
        this.client = client;
    }

    @Override
    public String engine() {
        return "whisper";
    }

    @Override
    public String transcribe(byte[] audioBytes) {
        return transcribe(audioBytes, "audio/ogg");
    }

    @Override
    public String transcribe(byte[] audioBytes, String mimeType) {
        return send(audioBytes, mimeType, null, null).text();
    }

    @Override
    public Transcription transcribe(byte[] audioBytes, String mimeType, String languageHint) {
        String hint = WhisperLanguages.hint(languageHint).orElse(null);
        return send(audioBytes, mimeType, VERBOSE_JSON, hint);
    }

    private Transcription send(byte[] audioBytes, String mimeType, String responseFormat, String language) {
        String apiKey = config.apiKey().filter(key -> !key.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "Whisper transcription requires qlawkus.messaging.transcription.api-key to be configured"));

        ClientMultipartForm form = ClientMultipartForm.create()
                .binaryFileUpload("file", "audio." + extensionFor(mimeType), Buffer.buffer(audioBytes), mimeType)
                .attribute("model", config.model(), null);
        if (responseFormat != null) {
            form.attribute("response_format", responseFormat, null);
        }
        if (language != null) {
            form.attribute("language", language, null);
        }

        WhisperApiClient.TranscriptionResponse parsed =
                client.transcribe(config.baseUrl(), "Bearer " + apiKey, form);
        String detected = VERBOSE_JSON.equals(responseFormat)
                ? WhisperLanguages.isoCode(parsed.language()).orElse(null)
                : null;
        return new Transcription(parsed.text(), detected);
    }

    private static String extensionFor(String mimeType) {
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "audio/wav", "audio/x-wav" -> "wav";
            case "audio/webm" -> "webm";
            case "audio/mpeg" -> "mp3";
            case "audio/mp4" -> "m4a";
            default -> "ogg";
        };
    }
}
