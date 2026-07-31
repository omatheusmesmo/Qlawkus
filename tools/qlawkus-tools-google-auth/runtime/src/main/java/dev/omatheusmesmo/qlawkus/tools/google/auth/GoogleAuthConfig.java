package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "qlawkus.google.auth")
public interface GoogleAuthConfig {

    /**
     * Google OAuth2 client ID. Optional so the agent boots with the Google tools dormant: the client
     * identifies the deployment, so it arrives with the deployment rather than being required before
     * the app will start.
     */
    Optional<String> clientId();

    /**
     * Google OAuth2 client secret. Optional for the same reason as {@link #clientId()}.
     */
    Optional<String> clientSecret();

    /**
     * Whether an OAuth client is configured at all. The Google tools are dormant until it is, so this
     * is the question callers ask before starting a flow rather than assuming one can run.
     */
    default boolean clientConfigured() {
        return present(clientId()) && present(clientSecret());
    }

    /**
     * The client ID, for a caller that has already established the flow can run. Optional at boot,
     * required at use: throwing here names the property to set, instead of letting a blank credential
     * reach Google and come back as an opaque rejection.
     */
    default String requireClientId() {
        return require(clientId(), "qlawkus.google.auth.client-id");
    }

    /** The client secret, with the same boot-optional/use-required contract as {@link #requireClientId()}. */
    default String requireClientSecret() {
        return require(clientSecret(), "qlawkus.google.auth.client-secret");
    }

    private static boolean present(Optional<String> value) {
        return value.filter(v -> !v.isBlank()).isPresent();
    }

    private static String require(Optional<String> value, String property) {
        return value.filter(v -> !v.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "Google is not authorized: " + property + " is not configured. Set it (or the "
                        + "matching GOOGLE_* environment variable) and restart."));
    }

    /**
     * Space-separated OAuth2 scopes for all Google tools. The Loopback/Web OAuth flow supports any scope (Calendar, Gmail, Drive full, Sheets, Storage, etc).
     */
    @WithDefault("openid email profile https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/gmail.modify https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.send https://www.googleapis.com/auth/drive https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/devstorage.read_write")
    String scopes();

    /**
     * Redirect URI registered in the Google Cloud Console OAuth client. For the loopback flow (Desktop app type), this is typically http://localhost:8080/api/google/oauth/callback. Must exactly match one of the URIs configured in the OAuth client.
     */
    @WithDefault("http://localhost:8080/api/google/oauth/callback")
    String redirectUri();
}
