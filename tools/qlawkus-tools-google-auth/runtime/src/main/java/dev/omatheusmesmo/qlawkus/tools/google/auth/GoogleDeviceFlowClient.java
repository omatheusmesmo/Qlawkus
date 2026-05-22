package dev.omatheusmesmo.qlawkus.tools.google.auth;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.FormParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST client for Google's OAuth 2.0 token endpoint. The name is legacy (formerly Device Flow only)
 * but now supports the Loopback/Web Authorization Code flow.
 */
@RegisterRestClient(baseUri = "https://oauth2.googleapis.com")
public interface GoogleDeviceFlowClient {

    @POST
    @Path("/token")
    TokenResponse refreshAccessToken(
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret,
            @FormParam("refresh_token") String refreshToken,
            @FormParam("grant_type") String grantType);

    @POST
    @Path("/token")
    TokenResponse exchangeAuthorizationCode(
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret,
            @FormParam("code") String code,
            @FormParam("redirect_uri") String redirectUri,
            @FormParam("grant_type") String grantType);
}
