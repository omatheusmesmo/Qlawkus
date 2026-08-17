package dev.omatheusmesmo.qlawkus.http;

import jakarta.ws.rs.core.Response;

/**
 * Shared body for a REST client's {@code @io.quarkus.rest.client.reactive.ClientExceptionMapper}
 * method: classify a non-2xx response as transient (429, 5xx) or permanent (everything else).
 * Returning {@code null} for a permanent failure lets Quarkus's default exception mapping apply, so
 * the caller still sees the real status and body - only the retry decision is centralized here.
 */
public final class HttpRetryClassifier {

    private HttpRetryClassifier() {
    }

    public static RuntimeException classify(Response response) {
        int status = response.getStatus();
        if (status == 429 || status >= 500) {
            return new TransientHttpException(status, response.getStatusInfo().getReasonPhrase());
        }
        return null;
    }
}
