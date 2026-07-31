package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;

/**
 * Starts the Google authorization flow from a browser, so the owner can authorize from the console
 * instead of having to ask the agent in chat. It is the entry-point counterpart to
 * {@link GoogleOAuthCallbackResource}: this issues the state and redirects out to Google, that one
 * takes the code back.
 *
 * <p>Deliberately a plain redirect endpoint rather than a service the console calls: the console must
 * not depend on this module (a distribution can omit Google entirely), so the only coupling it can
 * afford is a URL. The console renders a link to this path when the {@code google-workspace}
 * capability is in the baked manifest, and nothing here knows the console exists.
 *
 * <p>{@code @Authenticated}, unlike the callback: completing this flow binds a Google account to the
 * agent's credential vault, so it is an owner action. The callback stays open because Google is the
 * caller there, and it is guarded by the single-use state token this endpoint issues.
 */
@Path("/api/google/oauth/start")
@Authenticated
public class GoogleOAuthStartResource {

    @Inject
    GoogleAuthorizationUrls authorizationUrls;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response start() {
        if (!authorizationUrls.clientConfigured()) {
            Log.warn("GoogleOAuthStart: authorization requested but no OAuth client is configured");
            return Response.status(Response.Status.CONFLICT)
                    .entity(page("Google is not configured",
                            "This agent has no Google OAuth client. Set <code>GOOGLE_CLIENT_ID</code> and "
                            + "<code>GOOGLE_CLIENT_SECRET</code>, restart, then try again."))
                    .build();
        }
        String url = authorizationUrls.issue(null, null, null);
        return Response.seeOther(URI.create(url)).build();
    }

    private String page(String title, String body) {
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
                """.formatted(title, title, body);
    }
}
