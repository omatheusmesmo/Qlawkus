package dev.omatheusmesmo.qlawkus.messaging.slack;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "slack-api")
@Path("/api")
public interface SlackApiClient {

    @POST
    @Path("/chat.postMessage")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    void postMessage(
            @HeaderParam("Authorization") String authorization,
            PostMessageRequest request
    );

    record PostMessageRequest(
            String channel,
            String text,
            @JsonProperty("mrkdwn") boolean markdown
    ) {}
}
