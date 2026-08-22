package dev.omatheusmesmo.qlawkus.observability.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Build steps for the {@code qlawkus-observability} extension.
 *
 * <p>There is deliberately nothing here but the feature. The module carries no instrumentation: the
 * meters come from the presence of a Micrometer registry, which quarkus-langchain4j and Quarkus
 * itself already publish to. What the module actually contributes is a name - {@code
 * metadata.qlawkus.capability} in its {@code quarkus-extension.yaml} - because {@code ReactorCatalog}
 * reads that key from a reactor module, so without a module there is nowhere to declare it and
 * telemetry cannot be composed from {@code agent.yml} at all.
 *
 * <p>Qlawkus-owned meters land in {@code client} behind an optional {@code Instance<MeterRegistry>}
 * when the first one is written, not here.
 */
class ObservabilityProcessor {

    private static final String FEATURE = "qlawkus-observability";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
