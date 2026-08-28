package dev.omatheusmesmo.qlawkus.messaging.telegram;

import io.quarkus.arc.Arc;
import io.quarkus.logging.Log;
import io.quarkus.runtime.shutdown.ShutdownListener;

import java.time.Duration;
import java.util.Optional;

/**
 * Drains the Telegram long-poll thread across both shutdown phases. The split matters: stopping the
 * loop and waiting for it are different operations, and doing them together would make the wait
 * start before the other subsystems have been told to stop.
 *
 * <p>{@code preShutdown} clears the running flag and interrupts the thread, so no further getUpdates
 * is issued. {@code shutdown} joins it, which is the part the previous {@code @Observes
 * ShutdownEvent} never did: it interrupted and returned, so the process could exit with the poll
 * thread still inside a request.
 */
public class TelegramShutdownListener implements ShutdownListener {

    /** Slightly over the 25 second poll window would block a rollout; the interrupt should be quick. */
    private static final Duration JOIN_TIMEOUT = Duration.ofSeconds(5);

    @Override
    public void preShutdown(ShutdownNotification notification) {
        try {
            poller().ifPresent(TelegramPoller::requestStop);
        } catch (RuntimeException e) {
            Log.warnf(e, "Telegram: could not stop the poll loop on shutdown");
        } finally {
            notification.done();
        }
    }

    @Override
    public void shutdown(ShutdownNotification notification) {
        try {
            poller().ifPresent(poller -> {
                if (!poller.awaitStop(JOIN_TIMEOUT)) {
                    Log.warnf("Telegram: poll thread still running after %s, giving up the drain",
                            JOIN_TIMEOUT);
                }
            });
        } catch (RuntimeException e) {
            Log.warnf(e, "Telegram: waiting for the poll loop failed");
        } finally {
            notification.done();
        }
    }

    private static Optional<TelegramPoller> poller() {
        if (!Arc.container().isRunning()) {
            return Optional.empty();
        }
        return Optional.ofNullable(Arc.container().instance(TelegramPoller.class).get());
    }
}
