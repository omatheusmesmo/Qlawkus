package dev.omatheusmesmo.qlawkus.console.management;

import jakarta.enterprise.inject.spi.CDI;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.Readiness;

import java.util.List;
import java.util.function.UnaryOperator;

/** The only place that names the health API; loaded only once {@link ReadinessSource#available()} is true. */
final class ReadinessChecks {

    private ReadinessChecks() {
    }

    static List<ReadinessView> read(UnaryOperator<String> redact) {
        return ReadinessViews.of(CDI.current().select(HealthCheck.class, Readiness.Literal.INSTANCE), redact);
    }
}
