package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Selects the persistence backend for the cognition stores. Skills adopt it first; facts,
 * episodic journals and working memory migrate to the same SPI over time. Read at build time so
 * backend-specific beans and Flyway migrations can be wired conditionally.
 */
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "qlawkus.cognition")
public interface CognitionConfig {

    /**
     * The cognition persistence backend. {@code pgvector} is today's behavior (embeddings in
     * Postgres); {@code markdown} keeps curated content in files and needs no database;
     * {@code hybrid} uses files as the source of truth mirrored into pgvector. Defaults to
     * {@code pgvector} for backward compatibility.
     */
    @WithDefault("pgvector")
    Backend backend();

    enum Backend {
        MARKDOWN,
        PGVECTOR,
        HYBRID
    }
}
