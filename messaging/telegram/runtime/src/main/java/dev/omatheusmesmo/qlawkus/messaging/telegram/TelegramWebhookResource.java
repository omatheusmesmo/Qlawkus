package dev.omatheusmesmo.qlawkus.messaging.telegram;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/webhook/telegram")
public class TelegramWebhookResource {

    @Inject
    TelegramProviderAdapter adapter;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> webhook(TelegramUpdate update) {
        if (update == null || update.message() == null) {
            return Uni.createFrom().item(Response.ok().build());
        }

        Uni.createFrom().item(() -> adapter.mapUpdate(update))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .flatMap(adapter::receive)
                .subscribe().with(
                        r -> {},
                        err -> Log.errorf(err, "Telegram processing error updateId=%d", update.updateId()));

        return Uni.createFrom().item(Response.ok().build());
    }
}
