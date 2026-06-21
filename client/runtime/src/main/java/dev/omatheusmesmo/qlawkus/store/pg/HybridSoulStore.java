package dev.omatheusmesmo.qlawkus.store.pg;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Persona store for {@code qlawkus.cognition.backend=hybrid}: a straight reuse of
 * {@link PgSoulStore}, so hybrid keeps the singleton persona in Postgres like pgvector mode.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.cognition.backend", stringValue = "hybrid")
public class HybridSoulStore extends PgSoulStore {
}
