package dev.omatheusmesmo.qlawkus.messaging.telegram;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Duration;
import java.util.Optional;

/**
 * Long-polling ingestion for Telegram: repeatedly calls getUpdates on a
 * dedicated worker thread so it works behind NAT without a public webhook URL.
 * Each update is dispatched to a worker so the poll loop stays responsive, and
 * the loop survives transient failures instead of silently dying.
 */
@ApplicationScoped
public class TelegramPoller {

    private static final int POLL_TIMEOUT_SECONDS = 25;

    @Inject
    TelegramConfig config;

    @Inject
    @RestClient
    TelegramBotClient botClient;

    @Inject
    TelegramProviderAdapter adapter;

    private volatile boolean running;
    private Thread thread;

    void onStart(@Observes StartupEvent event) {
        if (!"polling".equalsIgnoreCase(config.mode())) {
            Log.info("Telegram: mode=webhook, long-polling disabled");
            return;
        }
        Optional<String> token = config.botToken().filter(t -> !t.isBlank());
        if (token.isEmpty()) {
            Log.info("Telegram: polling mode but bot-token not configured, skipping");
            return;
        }
        running = true;
        thread = new Thread(() -> pollLoop(token.get()), "telegram-poller");
        thread.setDaemon(true);
        thread.start();
        Log.info("Telegram: long-polling started");
    }

    /**
     * Stops the loop from starting another poll. Returns without waiting, so it can run in the
     * shutdown phase that refuses new work; {@link #awaitStop} is what actually waits.
     */
    void requestStop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    /**
     * Waits for the poll thread to finish, up to {@code timeout}. Returns whether it finished: a
     * caller that gets {@code false} has a thread still inside a 25 second getUpdates call, which is
     * worth logging rather than pretending the drain succeeded.
     */
    boolean awaitStop(Duration timeout) {
        if (thread == null) {
            return true;
        }
        try {
            thread.join(timeout.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return !thread.isAlive();
    }

    private void pollLoop(String token) {
        try {
            botClient.deleteWebhook(token);
        } catch (Exception e) {
            Log.warnf("Telegram: deleteWebhook before polling failed: %s", e.getMessage());
        }
        long offset = 0;
        while (running) {
            try {
                offset = pollOnce(token, offset);
            } catch (Exception e) {
                Log.warnf("Telegram: getUpdates failed, retrying: %s", e.getMessage());
                sleep();
            }
        }
        Log.info("Telegram: long-polling stopped");
    }

    long pollOnce(String token, long offset) {
        TelegramBotClient.GetUpdatesResponse response =
                botClient.getUpdates(token, offset, POLL_TIMEOUT_SECONDS);
        long nextOffset = offset;
        if (response != null && response.result() != null) {
            for (TelegramUpdate update : response.result()) {
                nextOffset = Math.max(nextOffset, update.updateId() + 1);
                if (update.message() != null) {
                    dispatch(update);
                }
            }
        }
        return nextOffset;
    }

    private void dispatch(TelegramUpdate update) {
        Uni.createFrom().item(() -> adapter.mapUpdate(update))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .flatMap(adapter::receive)
                .subscribe().with(
                        r -> {},
                        err -> Log.errorf(err, "Telegram poll processing error updateId=%d", update.updateId()));
    }

    private void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
