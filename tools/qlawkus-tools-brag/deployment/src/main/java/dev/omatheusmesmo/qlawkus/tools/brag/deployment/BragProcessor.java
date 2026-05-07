package dev.omatheusmesmo.qlawkus.tools.brag.deployment;

import dev.omatheusmesmo.qlawkus.tools.brag.AchievementProcessor;
import dev.omatheusmesmo.qlawkus.tools.brag.BragConfig;
import dev.omatheusmesmo.qlawkus.tools.brag.BragEntry;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

class BragProcessor {

    private static final String FEATURE = "brag";
    private static final String HIBERNATE_PANACHE_CAPABILITY = "io.quarkus.hibernate-orm-panache";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIf = IsBragEnabled.class)
    AdditionalBeanBuildItem registerBragBeans(Capabilities capabilities) {
        if (capabilities.isMissing(HIBERNATE_PANACHE_CAPABILITY)) {
            throw new ConfigurationException(
                    "Brag tool requires quarkus-hibernate-orm-panache. "
                            + "Add the extension or disable the brag tool with qlawkus.brag.enabled=false");
        }
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(BragEntry.class)
                .addBeanClass(BragConfig.class)
                .addBeanClass(AchievementProcessor.class)
                .setRemovable()
                .build();
    }

    @BuildStep(onlyIf = IsBragEnabled.class)
    ReflectiveClassBuildItem registerBragReflection() {
        return ReflectiveClassBuildItem.builder(
                AchievementProcessor.class.getName(),
                BragEntry.class.getName(),
                BragConfig.class.getName()
        ).methods().fields().build();
    }
}
