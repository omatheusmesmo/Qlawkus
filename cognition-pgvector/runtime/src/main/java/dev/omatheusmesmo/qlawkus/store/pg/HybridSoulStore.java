package dev.omatheusmesmo.qlawkus.store.pg;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;

/**
 * Persona store for {@code qlawkus.cognition.backend=hybrid}: a straight reuse of
 * {@link PgSoulStore}, so hybrid keeps the singleton persona in Postgres like pgvector mode.
 */
@ApplicationScoped
@Typed(HybridSoulStore.class)
public class HybridSoulStore extends PgSoulStore {
}
