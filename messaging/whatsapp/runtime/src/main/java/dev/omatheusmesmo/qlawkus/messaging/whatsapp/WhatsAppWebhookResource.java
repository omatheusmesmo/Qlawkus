package dev.omatheusmesmo.qlawkus.messaging.whatsapp;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/webhook/whatsapp")
public class WhatsAppWebhookResource {

    @Inject
    WhatsAppProviderAdapter adapter;

    @Inject
    WhatsAppConfig config;

    @GET
    public Response verify(
            @QueryParam("hub.mode") String mode,
            @QueryParam("hub.verify_token") String verifyToken,
            @QueryParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && config.verifyToken().equals(verifyToken)) {
            Log.info("WhatsApp webhook verified");
            return Response.ok(challenge).build();
        }
        return Response.status(Response.Status.FORBIDDEN).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> webhook(WhatsAppEvent event) {
        if (event == null || event.entry() == null) {
            return Uni.createFrom().item(Response.ok().build());
        }

        event.entry().stream()
                .filter(e -> e.changes() != null)
                .flatMap(e -> e.changes().stream())
                .filter(c -> c.value() != null && c.value().messages() != null)
                .flatMap(c -> c.value().messages().stream())
                .forEach(message -> {
                    adapter.receive(adapter.mapEvent(message))
                            .subscribe().with(
                                    r -> {},
                                    err -> Log.errorf(err, "WhatsApp processing error msgId=%s", message.id()));
                });

        return Uni.createFrom().item(Response.ok().build());
    }
}
