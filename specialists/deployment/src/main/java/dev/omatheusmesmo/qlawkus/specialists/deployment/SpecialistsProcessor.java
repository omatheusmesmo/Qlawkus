package dev.omatheusmesmo.qlawkus.specialists.deployment;

import dev.omatheusmesmo.qlawkus.specialists.SpecialistAdminResource;
import dev.omatheusmesmo.qlawkus.specialists.SpecialistTools;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class SpecialistsProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("qlawkus-specialists");
    }

    @BuildStep
    AdditionalBeanBuildItem entryPoints() {
        return AdditionalBeanBuildItem.builder().setUnremovable()
                .addBeanClasses(SpecialistAdminResource.class, SpecialistTools.class).build();
    }
}
