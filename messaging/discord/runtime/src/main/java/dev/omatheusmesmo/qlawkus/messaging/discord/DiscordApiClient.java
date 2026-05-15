package dev.omatheusmesmo.qlawkus.messaging.discord;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "discord-api")
@Path("/api/v10")
public interface DiscordApiClient {

    @PATCH
    @Path("/webhooks/{applicationId}/{interactionToken}/messages/@original")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    void editOriginalResponse(
            @PathParam("applicationId") String applicationId,
            @PathParam("interactionToken") String interactionToken,
            EditMessageRequest request,
            @HeaderParam("Authorization") String authorization
    );

    record EditMessageRequest(String content) {}
}
