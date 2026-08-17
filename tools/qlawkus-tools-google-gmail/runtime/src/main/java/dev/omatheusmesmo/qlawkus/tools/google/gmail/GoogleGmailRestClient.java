package dev.omatheusmesmo.qlawkus.tools.google.gmail;

import dev.omatheusmesmo.qlawkus.http.HttpRetryClassifier;
import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleAuthHeadersFilter;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailMessage;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailMessageList;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailModifyRequest;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailSendRequest;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.time.temporal.ChronoUnit;

/**
 * {@code @Retry} covers one API call, not one tool operation - a tool that calls this client three
 * times must not multiply into nine attempts. Only {@link dev.omatheusmesmo.qlawkus.http.
 * TransientHttpException} (429, 5xx) and {@code ProcessingException} (network-level failure) are
 * retried; every other status - 401 will never pass, for instance - propagates immediately.
 */
@Path("/gmail/v1/users")
@RegisterRestClient(configKey = "google-gmail", baseUri = "https://www.googleapis.com")
@RegisterProvider(GoogleAuthHeadersFilter.class)
@Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
        jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
        retryOn = {TransientHttpException.class, jakarta.ws.rs.ProcessingException.class})
@ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
public interface GoogleGmailRestClient {

    @GET
    @Path("/{userId}/messages")
    GmailMessageList listMessages(
        @PathParam("userId") String userId,
        @QueryParam("maxResults") Integer maxResults,
        @QueryParam("q") String query);

    @GET
    @Path("/{userId}/messages/{messageId}")
    GmailMessage getMessage(
        @PathParam("userId") String userId,
        @PathParam("messageId") String messageId,
        @QueryParam("format") String format);

    @POST
    @Path("/{userId}/messages/send")
    GmailMessage sendMessage(
        @PathParam("userId") String userId,
        GmailSendRequest request);

    @POST
    @Path("/{userId}/messages/{messageId}/trash")
    void trashMessage(
        @PathParam("userId") String userId,
        @PathParam("messageId") String messageId);

    @POST
    @Path("/{userId}/messages/{messageId}/untrash")
    void untrashMessage(
            @PathParam("userId") String userId,
            @PathParam("messageId") String messageId);

    @POST
    @Path("/{userId}/messages/{messageId}/modify")
    void modifyMessage(
        @PathParam("userId") String userId,
        @PathParam("messageId") String messageId,
        GmailModifyRequest request);

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        return HttpRetryClassifier.classify(response);
    }
}
