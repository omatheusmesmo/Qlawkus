package dev.omatheusmesmo.qlawkus.messaging.telegram;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Path("/api/webhook/telegram")
public class TelegramWebhookResource {

    static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    @Inject
    TelegramProviderAdapter adapter;

    @Inject
    TelegramConfig config;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> webhook(@HeaderParam(SECRET_HEADER) String token, TelegramUpdate update) {
        var secret = config.webhookSecretToken();
        if (secret.isPresent() && !matches(secret.get(), token)) {
            return Uni.createFrom().item(Response.status(Response.Status.FORBIDDEN).build());
        }
        if (update == null || update.message() == null) {
            return Uni.createFrom().item(Response.ok().build());
        }

        if (secret.isEmpty() && update.message().hasAttachments()) {
            return Uni.createFrom().item(Response.status(Response.Status.FORBIDDEN).build());
        }

        Uni.createFrom().item(() -> adapter.mapUpdate(update))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .flatMap(adapter::receive)
                .subscribe().with(
                        r -> {},
                        err -> Log.errorf(err, "Telegram processing error updateId=%d", update.updateId()));

        return Uni.createFrom().item(Response.ok().build());
    }

    private static boolean matches(String secret, String token) {
        return token != null && MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }
}
