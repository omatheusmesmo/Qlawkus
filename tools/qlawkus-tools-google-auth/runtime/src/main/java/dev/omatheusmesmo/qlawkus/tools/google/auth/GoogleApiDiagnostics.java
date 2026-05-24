package dev.omatheusmesmo.qlawkus.tools.google.auth;

import jakarta.ws.rs.WebApplicationException;

/**
 * Translates HTTP exceptions from Google API REST clients into actionable messages for the agent.
 * Differentiates auth issues (401/403) from request format issues (400) so the LLM doesn't loop
 * into authorization tools when the actual problem is a malformed request.
 */
public final class GoogleApiDiagnostics {

    private GoogleApiDiagnostics() {}

    public static String diagnose(String operation, Exception e) {
        if (e instanceof WebApplicationException webEx) {
            int status = webEx.getResponse().getStatus();
            String body;
            try {
                body = webEx.getResponse().readEntity(String.class);
            } catch (Exception readEx) {
                body = "";
            }

            boolean insufficientScope = body.contains("insufficient") || body.contains("Insufficient")
                || body.contains("forbidden") && status == 403;

            return switch (status) {
                case 401 -> "Authorization error while trying to " + operation
                    + " (HTTP 401). The Google access token is missing or expired. "
                    + "Call `startGoogleAuthorization` to re-authorize, then retry.";
                case 403 -> insufficientScope
                    ? "Permission denied while trying to " + operation
                    + " (HTTP 403 - insufficient scope). The current authorization does not include the required permission. "
                    + "Call `startGoogleAuthorization` to re-authorize with the updated scopes, then retry."
                    : "Access forbidden while trying to " + operation + " (HTTP 403). "
                    + "The resource may not exist or you lack access. Body: " + body;
                case 400 -> "Bad request while trying to " + operation + ": " + body
                    + "\n\nThis is NOT an authorization problem. The request itself is malformed. "
                    + "Do not call any authorization tool. This is likely a bug; report to the operator.";
                case 404 -> "Not found while trying to " + operation + ": " + body
                    + "\n\nThe target resource does not exist or is not accessible to this account.";
                case 429 -> "Google rate-limited the request (HTTP 429). Wait a few seconds and retry.";
                default -> status >= 500
                    ? "Google API is temporarily unavailable (HTTP " + status + "). Try again in a moment."
                    : "Unexpected HTTP " + status + " while trying to " + operation + ": " + body;
            };
        }
        return "Unexpected error while trying to " + operation + ": "
            + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
}
