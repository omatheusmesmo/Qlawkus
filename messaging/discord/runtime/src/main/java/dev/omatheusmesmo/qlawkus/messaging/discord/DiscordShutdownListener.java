package dev.omatheusmesmo.qlawkus.messaging.discord;

import io.quarkus.arc.Arc;
import io.quarkus.logging.Log;
import io.quarkus.runtime.shutdown.ShutdownListener;

import java.time.Duration;

/**
 * Logs the Discord gateway out and waits for it. The previous {@code @Observes ShutdownEvent} called
 * {@code logout().subscribe(...)} and returned, so the logout was a request the process was free to
 * outlive: the JVM could exit with the websocket still open, leaving Discord to time the session out
 * and the bot showing as online for a while after the pod was gone.
 *
 * <p>Blocking here is safe because Quarkus bounds the whole phase with
 * {@code quarkus.shutdown.timeout}, and the block carries its own smaller timeout so one unreachable
 * gateway cannot consume the entire budget that the other subsystems also need.
 */
public class DiscordShutdownListener implements ShutdownListener {

    private static final Duration LOGOUT_TIMEOUT = Duration.ofSeconds(5);

    @Override
    public void shutdown(ShutdownNotification notification) {
        try {
            logoutAndWait();
        } catch (RuntimeException e) {
            Log.warnf("Discord: logout did not complete cleanly on shutdown: %s", e.getMessage());
        } finally {
            notification.done();
        }
    }

    private void logoutAndWait() {
        if (!Arc.container().isRunning()) {
            return;
        }
        DiscordProviderAdapter adapter = Arc.container().instance(DiscordProviderAdapter.class).get();
        if (adapter == null || adapter.gatewayClient == null) {
            return;
        }
        adapter.gatewayClient.logout().block(LOGOUT_TIMEOUT);
        Log.info("Discord: Gateway logged out");
    }
}
