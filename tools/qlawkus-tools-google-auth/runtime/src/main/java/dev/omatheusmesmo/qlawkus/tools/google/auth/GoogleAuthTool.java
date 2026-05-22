package dev.omatheusmesmo.qlawkus.tools.google.auth;

import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.agent.Logged;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@ClawTool
@ApplicationScoped
@Logged
public class GoogleAuthTool {

    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";

    @Inject
    GoogleAuthConfig config;

    @Inject
    GoogleOAuthStateStore stateStore;

    @Tool("""
            Builds a Google OAuth authorization URL the user must visit to grant the app access to their \
            Google account (Calendar, Gmail, Drive, Sheets, Storage, etc). Use this when any Google API \
            call returns 401 Unauthorized, 403 Forbidden, or an invalid_grant error indicating missing or \
            expired authorization. \
            \
            IMPORTANT: This tool returns a multi-line message containing the authorization URL. You MUST \
            relay that exact message back to the user verbatim — do not summarize, paraphrase, or omit \
            the URL. After the user clicks the URL and authorizes, the app will receive the credentials \
            automatically via a redirect; the user does NOT need to copy any code back. Wait for the user \
            to say they've authorized before retrying the original API call. \
            \
            On internal configuration errors (e.g. missing client id), this tool returns a diagnostic \
            message instead of throwing. Forward that diagnostic verbatim to the user.""")
    public String startGoogleAuthorization() {
        if (config.clientId() == null || config.clientId().isBlank()) {
            return "⚠️ Google OAuth client is not configured. The operator needs to set GOOGLE_CLIENT_ID and "
                    + "GOOGLE_CLIENT_SECRET environment variables.";
        }

        String state = stateStore.issue();
        String url = AUTH_ENDPOINT
                + "?client_id=" + encode(config.clientId())
                + "&redirect_uri=" + encode(config.redirectUri())
                + "&response_type=code"
                + "&scope=" + encode(config.scopes())
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + encode(state);

        Log.infof("GoogleAuthTool: authorization URL generated, redirect_uri=%s", config.redirectUri());

        return String.format(
                "To authorize me to access your Google account, please open this URL in your browser:\n\n"
                        + "%s\n\n"
                        + "After signing in and approving the requested permissions, Google will redirect "
                        + "you back automatically. You'll see a confirmation page when it's done — just close "
                        + "it and let me know so I can retry your request.",
                url);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
