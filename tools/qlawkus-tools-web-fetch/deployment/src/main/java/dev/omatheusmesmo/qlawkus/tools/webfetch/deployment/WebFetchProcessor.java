package dev.omatheusmesmo.qlawkus.tools.webfetch.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/** Registers the optional web fetch capability as a Quarkus feature. */
class WebFetchProcessor {
    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("qlawkus-web-fetch");
    }
}
