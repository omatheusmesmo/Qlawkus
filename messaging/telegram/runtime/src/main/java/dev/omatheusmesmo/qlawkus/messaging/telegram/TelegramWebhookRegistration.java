package dev.omatheusmesmo.qlawkus.messaging.telegram;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.regex.Pattern;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/** Sets the webhook when an operator supplies a URL. Existing manual registrations can omit it. */
@ApplicationScoped
public class TelegramWebhookRegistration {

    private static final Pattern SECRET_TOKEN = Pattern.compile("[A-Za-z0-9_-]{1,256}");

    @Inject
    TelegramConfig config;

    @Inject
    @RestClient
    TelegramBotClient botClient;

    void start(@Observes StartupEvent ignored) {
        var secret = config.webhookSecretToken();
        if (secret.isPresent() && !SECRET_TOKEN.matcher(secret.get()).matches()) {
            throw new IllegalStateException("Telegram webhook secret must contain 1 to 256 letters, digits, underscores or hyphens.");
        }
        var url = config.webhookUrl();
        if (!"webhook".equalsIgnoreCase(config.mode()) || url.isEmpty()) {
            return;
        }
        if (!isHttpsUrl(url.get())) {
            throw new IllegalStateException("Telegram webhook URL must be a public HTTPS URL.");
        }
        var token = config.botToken().filter(value -> !value.isBlank());
        if (token.isEmpty()) {
            Log.warn("Telegram: bot token required to register the webhook");
            return;
        }
        var request = new TelegramBotClient.SetWebhookRequest(url.get(), secret.orElse(null));
        Uni.createFrom().deferred(() -> botClient.setWebhook(token.get(), request)).subscribe().with(response -> {
            if (response != null && response.ok() && response.result()) {
                Log.info("Telegram: webhook registered");
            } else {
                Log.warn("Telegram: webhook registration rejected");
            }
        }, failure -> Log.warnf("Telegram: webhook registration failed (%s)", failure.getClass().getSimpleName()));
    }

    private static boolean isHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getRawUserInfo() == null && uri.getRawFragment() == null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
