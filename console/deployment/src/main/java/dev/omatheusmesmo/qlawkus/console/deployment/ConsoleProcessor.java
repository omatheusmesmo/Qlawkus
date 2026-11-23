package dev.omatheusmesmo.qlawkus.console.deployment;

import dev.omatheusmesmo.qlawkus.console.ConsoleResource;
import dev.omatheusmesmo.qlawkus.console.ConsoleNavigation;
import dev.omatheusmesmo.qlawkus.console.ConsoleFeatures;
import dev.omatheusmesmo.qlawkus.console.ConsoleStatus;
import dev.omatheusmesmo.qlawkus.console.management.ChatConsoleResource;
import dev.omatheusmesmo.qlawkus.console.management.ArtifactsConsoleResource;
import dev.omatheusmesmo.qlawkus.console.management.CognitionConsoleResource;
import dev.omatheusmesmo.qlawkus.console.management.ConfigConsoleResource;
import dev.omatheusmesmo.qlawkus.console.management.MemoryConsoleResource;
import dev.omatheusmesmo.qlawkus.console.management.McpConsoleResource;
import dev.omatheusmesmo.qlawkus.console.management.OverviewConsoleResource;
import dev.omatheusmesmo.qlawkus.console.management.ReadinessSource;
import dev.omatheusmesmo.qlawkus.console.management.ScheduleConsoleResource;
import dev.omatheusmesmo.qlawkus.console.management.SkillsConsoleResource;
import dev.omatheusmesmo.qlawkus.console.management.SpecialistsConsoleResource;
import dev.omatheusmesmo.qlawkus.console.onboarding.OnboardingResource;
import dev.omatheusmesmo.qlawkus.console.onboarding.SetupState;
import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.qute.TemplateData;
import io.quarkus.qute.deployment.TemplateDataBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import java.util.List;

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

    private static final String CONSOLE_PACKAGE = "dev.omatheusmesmo.qlawkus.console.";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem consoleBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(ConsoleResource.class, ConsoleStatus.class, SetupState.class,
                        OnboardingResource.class, MemoryConsoleResource.class, SkillsConsoleResource.class,
                        CognitionConsoleResource.class, ConfigConsoleResource.class, ScheduleConsoleResource.class,
                        ChatConsoleResource.class, ConsoleNavigation.class, ConsoleFeatures.class, McpConsoleResource.class,
                        ArtifactsConsoleResource.class, SpecialistsConsoleResource.class,
                        OverviewConsoleResource.class)
                .setUnremovable()
                .build();
    }

    @BuildStep
    AdditionalBeanBuildItem readinessSource(Capabilities capabilities) {
        if (!capabilities.isPresent(Capability.SMALLRYE_HEALTH)) {
            return null;
        }
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(ReadinessSource.class)
                .setUnremovable()
                .build();
    }

    @BuildStep
    ReflectiveClassBuildItem skillSummaryForReflection() {
        return ReflectiveClassBuildItem.builder(SkillSummary.class.getName())
                .methods()
                .build();
    }

    /**
     * A template reads a record through reflection in a JVM, but a native image has none, so the page
     * answers 500 unless Qute generated a value resolver for the type. Asking for one per console
     * record, instead of annotating each view, means a view added later cannot be forgotten.
     */
    @BuildStep
    void consoleRecordResolvers(CombinedIndexBuildItem index, BuildProducer<TemplateDataBuildItem> templateData) {
        DotName marker = DotName.createSimple(TemplateData.class);
        for (ClassInfo type : index.getIndex().getKnownClasses()) {
            if (type.isRecord() && type.name().toString().startsWith(CONSOLE_PACKAGE) && !type.hasDeclaredAnnotation(marker)) {
                templateData.produce(new TemplateDataBuildItem(AnnotationInstance.create(marker, type, List.of()), type));
            }
        }
    }
}
