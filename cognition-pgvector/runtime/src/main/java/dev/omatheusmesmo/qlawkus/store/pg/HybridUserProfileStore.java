package dev.omatheusmesmo.qlawkus.store.pg;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;

/**
 * Owner-profile store for {@code qlawkus.cognition.backend=hybrid}: a straight reuse of
 * {@link PgUserProfileStore}, so hybrid keeps the singleton owner profile in Postgres like pgvector
 * mode.
 */
@ApplicationScoped
@Typed(HybridUserProfileStore.class)
public class HybridUserProfileStore extends PgUserProfileStore {
}
