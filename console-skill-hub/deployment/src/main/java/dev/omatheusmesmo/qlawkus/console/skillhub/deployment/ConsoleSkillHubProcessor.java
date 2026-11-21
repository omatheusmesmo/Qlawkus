package dev.omatheusmesmo.qlawkus.console.skillhub.deployment;

import dev.omatheusmesmo.qlawkus.console.skillhub.SkillHubConsoleFeature;
import dev.omatheusmesmo.qlawkus.console.skillhub.SkillInstallConsoleResource;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Build steps for the optional {@code qlawkus-console-skill-hub} extension. Its page and feature bean live in a
 * runtime jar that is not a CDI bean archive on its own, so they are registered here, the same way the console
 * registers its own.
 */
class ConsoleSkillHubProcessor {

    private static final String FEATURE = "qlawkus-console-skill-hub";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem skillHubBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(SkillInstallConsoleResource.class, SkillHubConsoleFeature.class)
                .setUnremovable()
                .build();
    }
}
