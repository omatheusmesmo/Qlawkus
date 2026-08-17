package dev.omatheusmesmo.qlawkus.messaging.whatsapp;

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
import jakarta.ws.rs.PathParam;
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
@RegisterRestClient(configKey = "whatsapp-api")
@Path("/v18.0")
@Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
        jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
        retryOn = {TransientHttpException.class, jakarta.ws.rs.ProcessingException.class})
@ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
public interface WhatsAppApiClient {

    @POST
    @Path("/{phoneNumberId}/messages")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    void sendMessage(
            @PathParam("phoneNumberId") String phoneNumberId,
            @HeaderParam("Authorization") String authorization,
            SendMessageRequest request
    );

    record SendMessageRequest(
            @JsonProperty("messaging_product") String messagingProduct,
            @JsonProperty("recipient_type") String recipientType,
            String to,
            String type,
            TextBody text
    ) {
        static SendMessageRequest textTo(String to, String body) {
            return new SendMessageRequest("whatsapp", "individual", to, "text", new TextBody(body));
        }
    }

    record TextBody(String body) {}

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        return HttpRetryClassifier.classify(response);
    }
}
