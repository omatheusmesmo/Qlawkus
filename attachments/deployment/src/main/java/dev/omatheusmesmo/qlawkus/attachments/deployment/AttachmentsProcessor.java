package dev.omatheusmesmo.qlawkus.attachments.deployment;

import dev.omatheusmesmo.qlawkus.attachments.ModelAttachmentChat;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.NativeImageEnableAllCharsetsBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;

class AttachmentsProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("qlawkus-attachments");
    }

    @BuildStep
    AdditionalBeanBuildItem attachmentChat() {
        return AdditionalBeanBuildItem.unremovableOf(ModelAttachmentChat.class);
    }

    @BuildStep
    NativeImageResourcePatternsBuildItem pdfResources() {
        return NativeImageResourcePatternsBuildItem.builder()
                .includePattern("org/apache/pdfbox/resources/.*")
                .includePattern("org/apache/fontbox/(cmap|unicode)/.*")
                .build();
    }

    @BuildStep
    NativeImageEnableAllCharsetsBuildItem pdfCharsets() {
        return new NativeImageEnableAllCharsetsBuildItem();
    }

    @BuildStep
    void initializePdfFontsAtRuntime(BuildProducer<RuntimeInitializedClassBuildItem> classes) {
        classes.produce(new RuntimeInitializedClassBuildItem("org.apache.pdfbox.pdmodel.font.PDType1Font"));
        classes.produce(new RuntimeInitializedClassBuildItem("org.apache.pdfbox.text.LegacyPDFStreamEngine"));
    }
}
