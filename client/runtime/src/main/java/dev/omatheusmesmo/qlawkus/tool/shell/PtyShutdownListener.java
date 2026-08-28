package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.dto.SessionInfo;
import io.quarkus.arc.Arc;
import io.quarkus.logging.Log;
import io.quarkus.runtime.shutdown.ShutdownListener;

/**
 * Closes every live PTY session when the application stops, so a rollout does not leave the child
 * processes behind. Without this the container exits while its shells are still running and the
 * kernel reparents them, which shows up as processes that outlive the pod that started them.
 *
 * <p>This runs in the {@code shutdown} phase rather than {@code preShutdown}: closing a session
 * kills its process, so it belongs with releasing resources, not with refusing new work.
 *
 * <p>The listener is registered as a build item, which means the instance is created during
 * augmentation - before any container exists. It therefore holds no bean reference and resolves
 * {@link PtySessionManager} from Arc at shutdown time.
 */
public class PtyShutdownListener implements ShutdownListener {

    @Override
    public void shutdown(ShutdownNotification notification) {
        try {
            closeLiveSessions();
        } catch (RuntimeException e) {
            Log.warnf(e, "PTY: draining sessions on shutdown failed");
        } finally {
            notification.done();
        }
    }

    private void closeLiveSessions() {
        if (!Arc.container().isRunning()) {
            return;
        }
        PtySessionManager manager = Arc.container().instance(PtySessionManager.class).get();
        if (manager == null || manager.isNativeImageMode()) {
            return;
        }

        for (SessionInfo session : manager.listSessions()) {
            try {
                manager.closeSession(session.sessionId());
            } catch (RuntimeException e) {
                Log.warnf("PTY: could not close session %s on shutdown: %s",
                        session.sessionId(), e.getMessage());
            }
        }
    }
}
