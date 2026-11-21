package dev.omatheusmesmo.qlawkus.messaging.transcription;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.temporal.ChronoUnit;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.client.api.ClientMultipartForm;

/**
 * The OpenAI-compatible transcription endpoint. The base URL is configuration the owner can point
 * at any compatible server, so it is passed per call with {@link Url}; the client's own configured
 * URL is only the mandatory default.
 */
@RegisterRestClient(configKey = "whisper-api")
@Path("/v1/audio/transcriptions")
public interface WhisperApiClient {

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
            jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
            retryOn = {TransientHttpException.class, ProcessingException.class})
    @ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
    TranscriptionResponse transcribe(@Url String baseUrl,
                                     @HeaderParam("Authorization") String authorization,
                                     ClientMultipartForm form);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TranscriptionResponse(String text, String language) {}

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        return HttpRetryClassifier.classify(response);
    }
}
