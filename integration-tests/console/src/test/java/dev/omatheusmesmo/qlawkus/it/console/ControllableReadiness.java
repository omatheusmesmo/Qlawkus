package dev.omatheusmesmo.qlawkus.it.console;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

/**
 * A readiness check the overview tests can flip between healthy, degraded and failing, so the Health
 * card can be driven through every state without breaking a real subsystem. It always carries a
 * data entry that is not a reason, which the card must never render.
 */
@Readiness
@ApplicationScoped
public class ControllableReadiness implements HealthCheck {

    public static final String NAME = "console-it-controllable";
    public static final String NOT_A_REASON = "s3cr3t-value-not-for-the-page";

    private volatile String failure;
    private volatile String degraded;

    public void failWith(String reason) {
        this.failure = reason;
    }

    public void degradeWith(String store) {
        this.degraded = store;
    }

    public void recover() {
        this.failure = null;
        this.degraded = null;
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder response = HealthCheckResponse.named(NAME)
                .withData("apiToken", NOT_A_REASON);
        if (failure != null) {
            return response.down().withData("error", failure).build();
        }
        if (degraded != null) {
            response.withData("degraded", degraded);
        }
        return response.up().build();
    }
}
