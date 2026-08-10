package dev.omatheusmesmo.qlawkus.health;

import dev.omatheusmesmo.qlawkus.store.SoulStore;
import dev.omatheusmesmo.qlawkus.store.UserProfileStore;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

/**
 * Reports whether the agent can reach the two singletons every turn depends on: the persona and the
 * owner profile. {@code SoulEngine} loads both to build the system message, so a pod that cannot read
 * them cannot answer at all - it must not receive traffic.
 *
 * <p>The check goes through the {@link SoulStore} and {@link UserProfileStore} SPIs rather than
 * inspecting a directory or a table, which is what makes it correct for every backend without a
 * single conditional: markdown reads a file, pgvector reads a row, hybrid reads whichever it treats
 * as source of truth. A check written against one backend's storage would report a false failure on
 * the others, and the backend is chosen at build time.
 *
 * <p>This is the check that catches the failure a container makes easy and silent: the state volume
 * mounted with the wrong ownership, so the markdown stores cannot read or seed their files.
 */
@Readiness
public class CognitionReadinessCheck implements HealthCheck {

    static final String NAME = "qlawkus-cognition";

    @Inject
    SoulStore soulStore;

    @Inject
    UserProfileStore userProfileStore;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder response = HealthCheckResponse.named(NAME);
        try {
            soulStore.load();
        } catch (RuntimeException e) {
            return response.down().withData("soul", describe(e)).build();
        }
        try {
            userProfileStore.load();
        } catch (RuntimeException e) {
            return response.down().withData("owner", describe(e)).build();
        }
        return response.up().build();
    }

    /**
     * Renders the failure as type plus message. The message alone is often empty on IO failures, and
     * the value is surfaced on an unauthenticated endpoint, so it stays a summary rather than a stack
     * trace.
     */
    private static String describe(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + message;
    }
}
