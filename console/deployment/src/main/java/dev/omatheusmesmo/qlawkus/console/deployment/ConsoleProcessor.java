package dev.omatheusmesmo.qlawkus.console.deployment;

import dev.omatheusmesmo.qlawkus.console.ConsoleResource;
import dev.omatheusmesmo.qlawkus.console.ConsoleStatus;
import dev.omatheusmesmo.qlawkus.console.management.CognitionConsoleResource;
import dev.omatheusmesmo.qlawkus.console.management.MemoryConsoleResource;
import dev.omatheusmesmo.qlawkus.console.management.SkillsConsoleResource;
import dev.omatheusmesmo.qlawkus.console.onboarding.OnboardingResource;
import dev.omatheusmesmo.qlawkus.console.onboarding.SetupState;
import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

/**
 * Build steps for the optional {@code qlawkus-console} extension: the server-rendered admin UI.
 *
 * <p>The console's beans live in this extension's runtime jar, which is not a CDI bean archive by
 * default, so they are registered explicitly here. {@link ConsoleResource} is the JAX-RS endpoint
 * and {@link ConsoleStatus} the data it renders; both are made unremovable so ArC keeps them even
 * though nothing else in the app references them directly.
 */
class ConsoleProcessor {

    private static final String FEATURE = "qlawkus-console";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem consoleBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(ConsoleResource.class, ConsoleStatus.class, SetupState.class,
                        OnboardingResource.class, MemoryConsoleResource.class, SkillsConsoleResource.class,
                        CognitionConsoleResource.class)
                .setUnremovable()
                .build();
    }

    @BuildStep
    ReflectiveClassBuildItem skillSummaryForReflection() {
        return ReflectiveClassBuildItem.builder(SkillSummary.class.getName())
                .methods()
                .build();
    }
}
