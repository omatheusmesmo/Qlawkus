package dev.omatheusmesmo.qlawkus.store.pg;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Owner-profile store for {@code qlawkus.cognition.backend=hybrid}: a straight reuse of
 * {@link PgUserProfileStore}, so hybrid keeps the singleton owner profile in Postgres like pgvector
 * mode.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.cognition.backend", stringValue = "hybrid")
public class HybridUserProfileStore extends PgUserProfileStore {
}
