package dev.omatheusmesmo.qlawkus.store.pg.health;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

/**
 * Reports whether the schema is at the version this build expects.
 *
 * <p>The built-in datasource check answers "is Postgres reachable", which is a different question: a
 * pod pointed at a database that is behind on migrations connects successfully, passes readiness, and
 * then fails at query time - the worst place to discover it.
 *
 * <p>This check lives in {@code qlawkus-cognition-pgvector} rather than in {@code qlawkus-client}
 * because it is meaningless anywhere else. A markdown-only build has no datasource and no migrations,
 * so the check must be absent there rather than conditional - the same module-level reasoning that
 * keeps the whole Postgres backend out of {@code client}.
 *
 * <p>Under the shipped defaults ({@code quarkus.flyway.migrate-at-start=true}) this reports UP on
 * every poll, because the agent applies its own migrations before it serves anything. It earns its
 * place in the deployments that turn that off: where the application has no DDL rights and a DBA
 * applies migrations out of band, or where a rolling deploy puts a pod carrying a newer migration set
 * in front of a database that has not been upgraded yet. In both cases the pod should stay out of the
 * load balancer until the schema catches up, and flip to ready on its own once it does - which is why
 * this queries live rather than caching a verdict from startup.
 */
@Readiness
public class MigrationReadinessCheck implements HealthCheck {

    static final String NAME = "qlawkus-migrations";

    @Inject
    Instance<Flyway> flyway;

    @Override
    public HealthCheckResponse call() {
        if (!flyway.isResolvable()) {
            return HealthCheckResponse.named(NAME).up().withData("flyway", "unavailable").build();
        }
        try {
            MigrationInfo[] pending = flyway.get().info().pending();
            if (pending.length > 0) {
                return HealthCheckResponse.named(NAME)
                        .down()
                        .withData("pending", pending.length)
                        .withData("nextVersion", describe(pending[0]))
                        .build();
            }
            return HealthCheckResponse.named(NAME).up().withData("pending", 0).build();
        } catch (RuntimeException e) {
            return HealthCheckResponse.named(NAME)
                    .down()
                    .withData("error", e.getClass().getSimpleName())
                    .build();
        }
    }

    private static String describe(MigrationInfo info) {
        return info.getVersion() == null ? info.getDescription() : info.getVersion().toString();
    }
}
