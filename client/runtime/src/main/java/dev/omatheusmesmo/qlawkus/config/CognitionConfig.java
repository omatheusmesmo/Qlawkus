package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Selects the persistence backend for the cognition stores. Read when the application boots, so one
 * build serves both the database-free quickstart and a Postgres deployment.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.cognition")
public interface CognitionConfig {

    /**
     * The cognition persistence backend. {@code markdown} keeps curated content in files and needs no
     * database; {@code pgvector} puts embeddings in Postgres; {@code hybrid} uses files as the source
     * of truth mirrored into pgvector.
     *
     * <p>The value only selects among backends that are actually on the classpath. The markdown
     * stores are the {@code @DefaultBean}, so a build without the {@code cognition-pgvector} extension
     * runs on files whatever this says. With the extension present and nothing set, a configured
     * {@code quarkus.datasource.jdbc.url} means {@code pgvector} and its absence means {@code markdown},
     * which also switches the datasource, Hibernate ORM and Flyway off. A value set here wins over both.
     * The declared default is therefore {@code markdown} only when no datasource URL is configured.
     *
     * <p>Allowed values, case-insensitive: {@code markdown}, {@code pgvector}, {@code hybrid}. Any
     * other value fails the boot with a configuration error.
     */
    @WithDefault("markdown")
    Backend backend();

    enum Backend {
        MARKDOWN,
        PGVECTOR,
        HYBRID
    }
}
