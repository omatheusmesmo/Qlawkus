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
     * Space-separated OAuth2 scopes for all Google tools.
     */
    @WithDefault("openid email profile https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.send https://www.googleapis.com/auth/drive https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/devstorage.read_write")
    String scopes();
}
