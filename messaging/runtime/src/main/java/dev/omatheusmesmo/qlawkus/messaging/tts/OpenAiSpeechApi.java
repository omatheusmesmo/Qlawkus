package dev.omatheusmesmo.qlawkus.messaging.tts;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.omatheusmesmo.qlawkus.http.HttpRetryClassifier;
import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.Url;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.temporal.ChronoUnit;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * An OpenAI-compatible speech endpoint. Each TTS provider carries its own base URL, so it is passed
 * per call with {@link Url}; the client's configured URL is only the mandatory default. A null
 * {@code authorization} sends no header, for local servers that need none.
 */
@RegisterRestClient(configKey = "tts-openai-api")
@Path("/v1/audio/speech")
public interface OpenAiSpeechApi {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
            jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
            retryOn = {TransientHttpException.class, ProcessingException.class})
    @ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
    byte[] speech(@Url String baseUrl, @HeaderParam("Authorization") String authorization, SpeechRequest request);

    record SpeechRequest(
            String model,
            String input,
            String voice,
            @JsonProperty("response_format") String responseFormat) {}

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        return HttpRetryClassifier.classify(response);
    }
}
