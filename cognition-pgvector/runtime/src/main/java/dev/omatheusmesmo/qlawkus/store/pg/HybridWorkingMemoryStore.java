package dev.omatheusmesmo.qlawkus.store.pg;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Working-memory store for {@code qlawkus.cognition.backend=hybrid}. The rolling chat log is
 * high-churn, transient state, not git-versionable knowledge, so hybrid keeps it in Postgres just
 * like pgvector mode does: this is a straight reuse of {@link PgWorkingMemoryStore}, selected for
 * the {@code hybrid} backend. (Facts, journals, and skills are the ones that get the file
 * source-of-truth + pg mirror in hybrid mode.)
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.cognition.backend", stringValue = "hybrid")
public class HybridWorkingMemoryStore extends PgWorkingMemoryStore {
}
