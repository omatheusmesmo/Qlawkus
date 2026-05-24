package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "qlawkus.google.auth")
public interface GoogleAuthConfig {

    /**
     * Google OAuth2 client ID.
     */
    String clientId();

    /**
     * Google OAuth2 client secret.
     */
    String clientSecret();

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
