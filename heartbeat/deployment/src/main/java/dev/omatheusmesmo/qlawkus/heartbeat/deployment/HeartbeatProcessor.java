package dev.omatheusmesmo.qlawkus.heartbeat.deployment;

import dev.omatheusmesmo.qlawkus.heartbeat.HeartbeatAdminResource;
import dev.omatheusmesmo.qlawkus.heartbeat.HeartbeatTool;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class HeartbeatProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("qlawkus-heartbeat");
    }

    @BuildStep
    AdditionalBeanBuildItem entryPoints() {
        return AdditionalBeanBuildItem.builder().setUnremovable()
                .addBeanClasses(HeartbeatTool.class, HeartbeatAdminResource.class).build();
    }
}
