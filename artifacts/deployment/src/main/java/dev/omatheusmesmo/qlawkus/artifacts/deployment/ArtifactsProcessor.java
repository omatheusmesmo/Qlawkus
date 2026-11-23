package dev.omatheusmesmo.qlawkus.artifacts.deployment;

import dev.omatheusmesmo.qlawkus.artifacts.ArtifactResource;
import dev.omatheusmesmo.qlawkus.artifacts.ArtifactTool;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class ArtifactsProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("qlawkus-artifacts");
    }

    @BuildStep
    AdditionalBeanBuildItem entryPoints() {
        return AdditionalBeanBuildItem.builder().setUnremovable()
                .addBeanClasses(ArtifactTool.class, ArtifactResource.class).build();
    }
}
