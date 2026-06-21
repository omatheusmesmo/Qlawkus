package dev.omatheusmesmo.qlawkus.store.pg.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Build steps for the {@code qlawkus-cognition-pgvector} extension: the Postgres/pgvector backend for
 * the cognition stores (facts, episodic journals, working memory, skills, soul, owner profile).
 * <p>
 * The store beans ({@code Pg*Store}, {@code Hybrid*Store}, the entities and repositories) carry CDI
 * scopes and are auto-discovered from this extension's Jandex index, so no explicit
 * {@code AdditionalBeanBuildItem} is needed. Backend selection across the module split is handled by
 * the {@code @IfBuildProperty(qlawkus.cognition.backend)} guards on the beans themselves; the markdown
 * {@code @DefaultBean} stores in {@code qlawkus-client} win whenever this extension is absent.
 */
class CognitionPgvectorProcessor {

    private static final String FEATURE = "qlawkus-cognition-pgvector";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
