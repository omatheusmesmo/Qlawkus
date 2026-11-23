package dev.omatheusmesmo.qlawkus.attachments.deployment;

import dev.omatheusmesmo.qlawkus.attachments.ModelAttachmentChat;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class AttachmentsProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("qlawkus-attachments");
    }

    @BuildStep
    AdditionalBeanBuildItem attachmentChat() {
        return AdditionalBeanBuildItem.unremovableOf(ModelAttachmentChat.class);
    }
}
