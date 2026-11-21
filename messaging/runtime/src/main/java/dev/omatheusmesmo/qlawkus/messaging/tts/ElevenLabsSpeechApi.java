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
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.temporal.ChronoUnit;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * The ElevenLabs text-to-speech endpoint. The base URL comes from the provider configuration and is
 * passed per call with {@link Url}; the client's configured URL is only the mandatory default.
 */
@RegisterRestClient(configKey = "tts-elevenlabs-api")
@Path("/v1/text-to-speech/{voice}")
public interface ElevenLabsSpeechApi {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("audio/mpeg")
    @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
            jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
            retryOn = {TransientHttpException.class, ProcessingException.class})
    @ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
    byte[] speech(@Url String baseUrl,
                  @PathParam("voice") String voice,
                  @QueryParam("output_format") String outputFormat,
                  @HeaderParam("xi-api-key") String apiKey,
                  SpeechRequest request);

    record SpeechRequest(String text, @JsonProperty("model_id") String modelId) {}

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        return HttpRetryClassifier.classify(response);
    }
}
