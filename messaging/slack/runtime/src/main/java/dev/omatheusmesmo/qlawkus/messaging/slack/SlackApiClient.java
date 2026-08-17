package dev.omatheusmesmo.qlawkus.messaging.slack;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.omatheusmesmo.qlawkus.http.HttpRetryClassifier;
import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import org.eclipse.microprofile.faulttolerance.Retry;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;

/**
 * {@code @Retry} covers one API call, not one message send - a caller that calls this client once
 * still only ever gets up to 4 attempts. Only {@link TransientHttpException} (429, 5xx) and
 * {@code ProcessingException} (network-level failure) are retried; every other status - a bad token
 * (401), for instance - propagates immediately since retrying it cannot succeed.
 */
@RegisterRestClient(configKey = "slack-api")
@Path("/api")
@Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
        jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
        retryOn = {TransientHttpException.class, jakarta.ws.rs.ProcessingException.class})
@ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
public interface SlackApiClient {

    @POST
    @Path("/chat.postMessage")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    void postMessage(
            @HeaderParam("Authorization") String authorization,
            PostMessageRequest request
    );

    record PostMessageRequest(
            String channel,
            String text,
            @JsonProperty("mrkdwn") boolean markdown
    ) {}

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        return HttpRetryClassifier.classify(response);
    }
}
