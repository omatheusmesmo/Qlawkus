package dev.omatheusmesmo.qlawkus.tools.google.auth;

import dev.omatheusmesmo.qlawkus.http.HttpRetryClassifier;
import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.Response;

import java.time.temporal.ChronoUnit;

/**
 * REST client for Google's OAuth 2.0 token endpoint. The name is legacy (formerly Device Flow only)
 * but now supports the Loopback/Web Authorization Code flow.
 *
 * <p>{@code @Retry} covers one API call, not one token exchange - a caller that calls this client
 * once still only ever gets up to 4 attempts. Only {@link TransientHttpException} (429, 5xx) and
 * {@code ProcessingException} (network-level failure) are retried; a rejected grant (400
 * {@code invalid_grant}, for instance) will never pass and propagates immediately.
 */
@RegisterRestClient(configKey = "google-device-flow", baseUri = "https://oauth2.googleapis.com")
@Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
        jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
        retryOn = {TransientHttpException.class, jakarta.ws.rs.ProcessingException.class})
@ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
public interface GoogleDeviceFlowClient {

    @POST
    @Path("/token")
    TokenResponse refreshAccessToken(
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret,
            @FormParam("refresh_token") String refreshToken,
            @FormParam("grant_type") String grantType);

    @POST
    @Path("/token")
    TokenResponse exchangeAuthorizationCode(
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret,
            @FormParam("code") String code,
            @FormParam("redirect_uri") String redirectUri,
            @FormParam("grant_type") String grantType);

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        return HttpRetryClassifier.classify(response);
    }
}
