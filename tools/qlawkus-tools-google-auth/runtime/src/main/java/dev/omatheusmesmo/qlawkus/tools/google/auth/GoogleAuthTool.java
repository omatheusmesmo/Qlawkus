package dev.omatheusmesmo.qlawkus.tools.google.auth;

import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.agent.Logged;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@QlawTool
@ApplicationScoped
@Logged
public class GoogleAuthTool {

    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";

    @Inject
    GoogleAuthConfig config;

    @Inject
    GoogleOAuthStateStore stateStore;

    @Inject
    CredentialVaultService vault;

    @Inject
    Instance<GoogleAuthDeliveryContext> deliveryContextInstance;

    private volatile String lastAuthorizationStatus;
    private volatile Instant lastAuthorizationAt;

    void onRefreshTokenCaptured(@ObservesAsync RefreshTokenCapturedEvent event) {
        lastAuthorizationStatus = "authorized";
        lastAuthorizationAt = Instant.now();
        Log.info("GoogleAuthTool: authorization confirmed via callback");
    }

    @Tool("Check if Google authorization is currently active. Returns the authorization status "
            + "and when it was last confirmed. Use this after the user says they authorized to verify "
            + "the callback was received before retrying any Google API calls.")
    public String checkGoogleAuthorization() {
        String token = vault.getAccessToken();
        boolean hasToken = token != null && !token.isBlank();
        String refreshToken = vault.getDecryptedRefreshToken();
        boolean hasRefreshToken = refreshToken != null && !refreshToken.isBlank();

        if (lastAuthorizationStatus != null) {
            return String.format(
                    "Google authorization status: %s (confirmed at %s). Access token available: %s. Refresh token persisted: %s.",
                    lastAuthorizationStatus, lastAuthorizationAt, hasToken, hasRefreshToken);
        }

        if (hasRefreshToken) {
            return String.format(
                    "Google authorization status: previously authorized (refresh token exists). Access token available: %s.",
                    hasToken);
        }

        return "Google authorization status: not_authorized. No refresh token found. "
                + "Call startGoogleAuthorization to begin the authorization flow.";
    }

    @Tool("""
            Builds a Google OAuth authorization URL the user must visit to grant the app access to their \
            Google account (Calendar, Gmail, Drive, Sheets, Storage, etc). Use this when any Google API \
            call returns 401 Unauthorized, 403 Forbidden, or an invalid_grant error indicating missing or \
            expired authorization. \
            \
            IMPORTANT: This tool returns a multi-line message containing the authorization URL. You MUST \
            relay that exact message back to the user verbatim - do not summarize, paraphrase, or omit \
            the URL. After the user clicks the URL and authorizes, use checkGoogleAuthorization to verify \
            the callback was received before retrying the original API call. \
            \
            On internal configuration errors (e.g. missing client id), this tool returns a diagnostic \
            message instead of throwing. Forward that diagnostic verbatim to the user.""")
    public String startGoogleAuthorization() {
        if (config.clientId() == null || config.clientId().isBlank()) {
            return "Google OAuth client is not configured. The operator needs to set GOOGLE_CLIENT_ID and "
                    + "GOOGLE_CLIENT_SECRET environment variables.";
        }

        String memoryId = null;
        String providerId = null;
        String chatId = null;
        if (!deliveryContextInstance.isUnsatisfied()) {
            GoogleAuthDeliveryContext ctx = deliveryContextInstance.get();
            memoryId = ctx.memoryId();
            providerId = ctx.providerId();
            chatId = ctx.chatId();
        }

        String state = stateStore.issue(memoryId, providerId, chatId);
        String url = AUTH_ENDPOINT
                + "?client_id=" + encode(config.clientId())
                + "&redirect_uri=" + encode(config.redirectUri())
                + "&response_type=code"
                + "&scope=" + encode(config.scopes())
                + "&prompt=consent"
                + "&state=" + encode(state);

        Log.infof("GoogleAuthTool: authorization URL generated, redirect_uri=%s memoryId=%s", config.redirectUri(), memoryId);

        return String.format(
                "To authorize me to access your Google account, please open this URL in your browser:\n\n"
                + "%s\n\n"
                + "After signing in and approving the requested permissions, Google will redirect "
                + "you back automatically. You'll see a confirmation page when it's done. "
                + "Then use checkGoogleAuthorization to verify the callback was received "
                + "before retrying your request.", url);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
