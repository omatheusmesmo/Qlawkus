package dev.omatheusmesmo.qlawkus.messaging.slack;

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

@Path("/api/webhook/slack")
public class SlackWebhookResource {

    @Inject
    SlackProviderAdapter adapter;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> webhook(SlackEvent event) {
        if (event == null) {
            return Uni.createFrom().item(Response.ok().build());
        }

        if (SlackEvent.TYPE_URL_VERIFICATION.equals(event.type())) {
            return Uni.createFrom().item(
                    Response.ok(new ChallengeResponse(event.challenge())).build());
        }

        if (SlackEvent.TYPE_EVENT_CALLBACK.equals(event.type()) && event.event() != null) {
            SlackEvent.InnerEvent inner = event.event();
            if ("message".equals(inner.type()) && inner.user() != null) {
                adapter.receive(adapter.mapEvent(inner))
                        .subscribe().with(
                                r -> {},
                                err -> Log.errorf(err, "Slack processing error user=%s", inner.user()));
            }
        }

        return Uni.createFrom().item(Response.ok().build());
    }

    record ChallengeResponse(@JsonProperty("challenge") String challenge) {}
}
