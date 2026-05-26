package dev.omatheusmesmo.qlawkus.tools.google.auth;

import dev.omatheusmesmo.qlawkus.agent.OAuthCallbackCompletedEvent;
import io.quarkus.logging.Log;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Path("/api/google/oauth/callback")
@PermitAll
public class GoogleOAuthCallbackResource {

    @Inject
    GoogleAuthConfig config;

    @Inject
    @RestClient
    GoogleDeviceFlowClient oauthClient;

    @Inject
    GoogleOAuthStateStore stateStore;

    @Inject
    Event<RefreshTokenCapturedEvent> refreshTokenEvent;

    @Inject
    Event<OAuthCallbackCompletedEvent> callbackCompletedEvent;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response callback(
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @QueryParam("error") String error,
            @QueryParam("error_description") String errorDescription) {

        if (error != null) {
            Log.warnf("GoogleOAuth callback: provider returned error=%s desc=%s", error, errorDescription);
            return Response.status(400)
                    .entity(htmlPage("Authorization failed",
                            "Google reported: <strong>" + escape(error) + "</strong><br>"
                            + (errorDescription != null ? escape(errorDescription) : "")))
                    .build();
        }

        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return Response.status(400)
                    .entity(htmlPage("Bad request", "Missing code or state parameter."))
                    .build();
        }

        GoogleOAuthState oauthState = stateStore.consume(state);
        if (oauthState == null) {
            Log.warn("GoogleOAuth callback: state token invalid or expired");
            return Response.status(400)
                    .entity(htmlPage("Invalid state",
                            "The authorization request expired or was already used. "
                            + "Please start the authorization flow again."))
                    .build();
        }

        try {
            TokenResponse response = oauthClient.exchangeAuthorizationCode(
                    config.clientId(),
                    config.clientSecret(),
                    code,
                    config.redirectUri(),
                    "authorization_code");

            if (response.error() != null) {
                Log.errorf("GoogleOAuth callback: token exchange error=%s desc=%s", response.error(), response.errorDescription());
                return Response.status(400)
                        .entity(htmlPage("Token exchange failed",
                                "Google rejected the code: " + escape(response.error())))
                        .build();
            }

            if (response.refreshToken() == null) {
                Log.warn("GoogleOAuth callback: no refresh token in response (user may have already consented)");
                return Response.status(400)
                        .entity(htmlPage("No refresh token",
                                "Google did not return a refresh token. Try revoking access in your Google "
                                + "account settings and authorize again."))
                        .build();
            }

            refreshTokenEvent.fireAsync(new RefreshTokenCapturedEvent(response.refreshToken()));
            Log.info("GoogleOAuth callback: authorization successful, refresh token captured");

            if (oauthState.memoryId != null) {
                callbackCompletedEvent.fireAsync(new OAuthCallbackCompletedEvent(
                        response.refreshToken(),
                        oauthState.memoryId,
                        oauthState.providerId,
                        oauthState.chatId));
                Log.infof("GoogleOAuth callback: fired OAuthCallbackCompletedEvent for memoryId=%s provider=%s",
                        oauthState.memoryId, oauthState.providerId);
            }

            return Response.ok(htmlPage("Authorized!",
                    "You can close this tab and return to the chat. "
                    + "I'll retry your request automatically."))
                    .build();
        } catch (Exception e) {
            Log.errorf(e, "GoogleOAuth callback: token exchange failed");
            return Response.status(500)
                    .entity(htmlPage("Unexpected error",
                            "Something went wrong exchanging the code: " + escape(e.getMessage())))
                    .build();
        }
    }

    private String htmlPage(String title, String body) {
        return """
                <!doctype html><html><head><meta charset="utf-8">
                <title>%s</title>
                <style>
                body{font-family:system-ui,sans-serif;max-width:520px;margin:80px auto;padding:0 24px;color:#222}
                h1{font-size:24px;margin-bottom:16px}
                p{line-height:1.5}
                .card{background:#f6f8fa;border:1px solid #d1d9e0;border-radius:8px;padding:24px}
                </style></head>
                <body><div class="card"><h1>%s</h1><p>%s</p></div></body></html>
                """.formatted(escape(title), escape(title), body);
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
