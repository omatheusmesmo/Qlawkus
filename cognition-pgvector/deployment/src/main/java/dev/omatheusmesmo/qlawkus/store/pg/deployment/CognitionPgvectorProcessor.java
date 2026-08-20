package dev.omatheusmesmo.qlawkus.store.pg.deployment;

import dev.omatheusmesmo.qlawkus.store.pg.health.MigrationReadinessCheck;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
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

    /**
     * Registers the migration readiness check, but only for a distribution that pulled in SmallRye
     * Health. The check carries {@code @Readiness} without a scope annotation, so nothing discovers it
     * on its own and the class stays unreferenced without the capability - which is what lets this
     * module depend on the health API optionally, exactly as {@code client} does.
     */
    @BuildStep
    void registerHealthChecks(Capabilities capabilities, BuildProducer<AdditionalBeanBuildItem> beans) {
        if (!capabilities.isPresent(Capability.SMALLRYE_HEALTH)) {
            return;
        }
        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass(MigrationReadinessCheck.class)
                .setDefaultScope(DotNames.APPLICATION_SCOPED)
                .setUnremovable()
                .build());
    }
}
