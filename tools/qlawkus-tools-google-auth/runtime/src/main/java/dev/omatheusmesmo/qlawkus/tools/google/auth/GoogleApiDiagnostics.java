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

            return switch (status) {
                case 401, 403 -> "⚠️ Authorization error while trying to " + operation
                        + " (HTTP " + status + "). The Google access token is missing, expired or lacks the required scope. "
                        + "Call the `startGoogleAuthorization` tool to start a new authorization flow, then retry.";
                case 400 -> "⚠️ Bad request while trying to " + operation + ": " + body
                        + "\n\nThis is NOT an authorization problem — the request itself is malformed. "
                        + "Do not call any authorization tool. This is likely a bug; report to the operator.";
                case 404 -> "⚠️ Not found while trying to " + operation + ": " + body
                        + "\n\nThe target resource does not exist or is not accessible to this account.";
                case 429 -> "⚠️ Google rate-limited the request (HTTP 429). Wait a few seconds and retry.";
                default -> status >= 500
                        ? "⚠️ Google API is temporarily unavailable (HTTP " + status + "). Try again in a moment."
                        : "⚠️ Unexpected HTTP " + status + " while trying to " + operation + ": " + body;
            };
        }
        return "⚠️ Unexpected error while trying to " + operation + ": "
                + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
}
