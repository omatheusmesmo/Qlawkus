package dev.omatheusmesmo.qlawkus.http;

/**
 * Marks an HTTP failure as retryable: a 429 (back off and try again), a 5xx (the server, not the
 * request, is the problem), or a network-level failure reaching the server at all. {@code @Retry} is
 * configured to retry only on this type - every other status (400, 401, 403, 404, ...) is permanent,
 * and retrying it would just delay surfacing a problem that will never resolve on its own.
 */
public class TransientHttpException extends RuntimeException {

    public TransientHttpException(int status, String message) {
        super(status + ": " + message);
    }

    public TransientHttpException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransientHttpException(String message) {
        super(message);
    }
}
