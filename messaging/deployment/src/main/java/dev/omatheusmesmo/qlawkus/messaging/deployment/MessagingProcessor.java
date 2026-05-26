package dev.omatheusmesmo.qlawkus.messaging.deployment;

import dev.omatheusmesmo.qlawkus.messaging.MessagingOrchestrator;
import dev.omatheusmesmo.qlawkus.messaging.NotificationService;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class MessagingProcessor {

    private static final String FEATURE = "qlawkus-messaging";
    private static final String AGENT_CAPABILITY = "dev.omatheusmesmo.qlawkus.agent";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem registerMessagingBeans(Capabilities capabilities) {
        AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder()
                .addBeanClass(NotificationService.class)
                .setUnremovable();

        if (capabilities.isPresent(AGENT_CAPABILITY)) {
            builder.addBeanClass(MessagingOrchestrator.class);
        }

        return builder.build();
    }
}
