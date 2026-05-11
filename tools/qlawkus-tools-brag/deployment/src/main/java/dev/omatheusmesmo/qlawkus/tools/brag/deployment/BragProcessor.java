package dev.omatheusmesmo.qlawkus.tools.brag.deployment;

import dev.omatheusmesmo.qlawkus.tools.brag.AchievementProcessor;
import dev.omatheusmesmo.qlawkus.tools.brag.BragCleanupJob;
import dev.omatheusmesmo.qlawkus.tools.brag.BragConfig;
import dev.omatheusmesmo.qlawkus.tools.brag.BragEntry;
import dev.omatheusmesmo.qlawkus.tools.brag.BragExportResource;
import dev.omatheusmesmo.qlawkus.tools.brag.BragTool;
import dev.omatheusmesmo.qlawkus.tools.brag.ImpactTranslator;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

class BragProcessor {

    private static final String FEATURE = "brag";
    private static final String REST_CAPABILITY = "io.quarkus.rest";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIf = IsBragEnabled.class)
    AdditionalBeanBuildItem registerBragBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(AchievementProcessor.class)
                .addBeanClass(BragCleanupJob.class)
                .addBeanClass(BragConfig.class)
                .addBeanClass(BragEntry.class)
                .addBeanClass(BragTool.class)
                .addBeanClass(ImpactTranslator.class)
                .setRemovable()
                .build();
    }

    @BuildStep(onlyIf = IsBragEnabled.class)
    AdditionalBeanBuildItem registerBragExportResource(Capabilities capabilities) {
        if (capabilities.isMissing(REST_CAPABILITY)) {
            return null;
        }
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(BragExportResource.class)
                .setRemovable()
                .build();
    }

    @BuildStep(onlyIf = IsBragEnabled.class)
    ReflectiveClassBuildItem registerBragReflection() {
        return ReflectiveClassBuildItem.builder(
                AchievementProcessor.class.getName(),
                BragCleanupJob.class.getName(),
                BragConfig.class.getName(),
                BragEntry.class.getName(),
                BragExportResource.class.getName(),
                BragTool.class.getName(),
                ImpactTranslator.class.getName()
        ).methods().fields().build();
    }
}
