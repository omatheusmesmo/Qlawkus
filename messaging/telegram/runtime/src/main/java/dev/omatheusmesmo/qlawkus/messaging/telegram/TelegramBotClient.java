package dev.omatheusmesmo.qlawkus.messaging.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.omatheusmesmo.qlawkus.http.HttpRetryClassifier;
import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import java.time.temporal.ChronoUnit;
import java.util.List;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * {@code @Retry} covers one API call, not one poll/send operation - a caller that calls a retried
 * method once still only ever gets up to 4 attempts. Only {@link TransientHttpException} (429, 5xx)
 * and {@code ProcessingException} (network-level failure) are retried; every other status - 401 will
 * never pass, for instance - propagates immediately.
 *
 * <p>{@link #getUpdates} is long-polling: the {@code timeout} query param tells Telegram to hold the
 * connection open for up to that many seconds waiting for new messages, returning a normal 200 with
 * an empty {@code result} array if nothing arrives. That is a successful response, not a failure -
 * {@code @Retry} never sees it, since it only reacts to an actual non-2xx status or a network error.
 *
 * <p>{@link #sendChatAction} is deliberately left without {@code @Retry}: it drives the best-effort
 * typing indicator, called synchronously on the message-receive path, and its caller already swallows
 * failures. Retrying it would only block that path for seconds without changing the outcome.
 */
@RegisterRestClient(configKey = "telegram-bot-api")
@Path("/bot{token}")
public interface TelegramBotClient {

    @POST
    @Path("/sendMessage")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
            jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
            retryOn = {TransientHttpException.class, jakarta.ws.rs.ProcessingException.class})
    @ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
    void sendMessage(@PathParam("token") String token, SendMessageRequest request);

    @GET
    @Path("/getFile")
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
            jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
            retryOn = {TransientHttpException.class, jakarta.ws.rs.ProcessingException.class})
    @ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
    GetFileResponse getFile(@PathParam("token") String token, @QueryParam("file_id") String fileId);

    @GET
    @Path("/getUpdates")
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
            jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
            retryOn = {TransientHttpException.class, jakarta.ws.rs.ProcessingException.class})
    @ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
    GetUpdatesResponse getUpdates(@PathParam("token") String token,
                                  @QueryParam("offset") long offset,
                                  @QueryParam("timeout") int timeout);

    @GET
    @Path("/deleteWebhook")
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
            jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
            retryOn = {TransientHttpException.class, jakarta.ws.rs.ProcessingException.class})
    @ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
    void deleteWebhook(@PathParam("token") String token);

    @POST
    @Path("/sendChatAction")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    void sendChatAction(@PathParam("token") String token, SendChatActionRequest request);

    record SendMessageRequest(
            @JsonProperty("chat_id") String chatId,
            String text,
            @JsonProperty("parse_mode") String parseMode
    ) {}

    record SendChatActionRequest(
            @JsonProperty("chat_id") String chatId,
            String action
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GetFileResponse(boolean ok, FileResult result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FileResult(@JsonProperty("file_path") String filePath) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GetUpdatesResponse(boolean ok, List<TelegramUpdate> result) {}

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        return HttpRetryClassifier.classify(response);
    }
}
