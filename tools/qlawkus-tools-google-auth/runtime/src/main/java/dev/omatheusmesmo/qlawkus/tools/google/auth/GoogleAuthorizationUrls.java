package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds the Google consent URL, and is the single place that knows how. Two entry points need it -
 * the {@code startGoogleAuthorization} tool (chat) and {@code /api/google/oauth/start} (browser) -
 * and both must issue a state token bound to the same redirect URI and scopes, or the callback
 * rejects the round trip. Keeping the construction here is what stops the two from drifting.
 */
@ApplicationScoped
public class GoogleAuthorizationUrls {

    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";

    @Inject
    GoogleAuthConfig config;

    @Inject
    GoogleOAuthStateStore stateStore;

    /**
     * Whether an OAuth client is configured at all. Callers surface this as a diagnostic rather than
     * sending the user to a URL Google would reject.
     */
    public boolean clientConfigured() {
        return config.clientConfigured();
    }

    /**
     * Issues a fresh state token and returns the consent URL to send the user to. The delivery
     * identifiers are carried through the state so the callback can notify the originating chat;
     * pass {@code null} for all three when the flow starts from a browser, where there is no chat to
     * answer and the confirmation is the callback page itself.
     */
    public String issue(String memoryId, String providerId, String chatId) {
        String state = stateStore.issue(memoryId, providerId, chatId);
        Log.infof("GoogleAuthorizationUrls: authorization URL generated, redirect_uri=%s memoryId=%s",
                config.redirectUri(), memoryId);
        return AUTH_ENDPOINT
                + "?client_id=" + encode(config.requireClientId())
                + "&redirect_uri=" + encode(config.redirectUri())
                + "&response_type=code"
                + "&scope=" + encode(config.scopes())
                + "&prompt=consent"
                + "&state=" + encode(state);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
