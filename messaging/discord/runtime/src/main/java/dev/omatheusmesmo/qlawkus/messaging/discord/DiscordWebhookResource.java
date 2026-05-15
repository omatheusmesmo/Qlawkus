package dev.omatheusmesmo.qlawkus.messaging.discord;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/webhook/discord")
public class DiscordWebhookResource {

    @Inject
    DiscordProviderAdapter adapter;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> webhook(DiscordInteraction interaction) {
        if (interaction == null) {
            return Uni.createFrom().item(Response.ok().build());
        }

        if (interaction.type() == DiscordInteraction.TYPE_PING) {
            return Uni.createFrom().item(
                    Response.ok(new PingResponse(1)).build());
        }

        adapter.receive(adapter.mapInteraction(interaction))
                .subscribe().with(
                        r -> {},
                        err -> Log.errorf(err, "Discord processing error id=%s", interaction.id()));

        return Uni.createFrom().item(
                Response.ok(new DeferredResponse(5)).build());
    }

    record PingResponse(int type) {}

    record DeferredResponse(
            int type,
            @JsonProperty("data") DeferredData data
    ) {
        DeferredResponse(int type) {
            this(type, new DeferredData(false));
        }

        record DeferredData(@JsonProperty("flags") boolean ephemeral) {}
    }
}
