package dev.omatheusmesmo.qlawkus.decision.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class DecisionProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("qlawkus-cognition-decision");
    }
}
