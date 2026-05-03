package dev.omatheusmesmo.qlawkus.tools.google.auth;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.FormParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/oauth2")
@RegisterRestClient(baseUri = "https://oauth2.googleapis.com")
public interface GoogleDeviceFlowClient {

    @POST
    @Path("/device/code")
    DeviceCodeResponse requestDeviceCode(
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret,
            @FormParam("scope") String scope);

    @POST
    @Path("/token")
    TokenResponse retrieveToken(
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret,
            @FormParam("device_code") String deviceCode,
            @FormParam("grant_type") String grantType);
}
