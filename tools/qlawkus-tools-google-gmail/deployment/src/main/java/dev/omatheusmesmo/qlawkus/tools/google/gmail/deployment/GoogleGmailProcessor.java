package dev.omatheusmesmo.qlawkus.tools.google.gmail.deployment;

import dev.omatheusmesmo.qlawkus.tools.google.gmail.GmailTool;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.GoogleGmailConfig;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.GoogleGmailRestClient;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailMessage;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailMessageList;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailMessageRef;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailSendRequest;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

class GoogleGmailProcessor {

    private static final String FEATURE = "google-gmail";
    private static final String REST_CLIENT_CAPABILITY = "io.quarkus.rest-client.jackson";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIf = IsGmailEnabled.class)
    AdditionalBeanBuildItem registerGmailBeans(Capabilities capabilities) {
        if (capabilities.isMissing(REST_CLIENT_CAPABILITY)) {
            throw new ConfigurationException(
                    "Gmail tool requires quarkus-rest-client-jackson. "
                            + "Add the extension or disable gmail with qlawkus.google.gmail.enabled=false");
        }
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(GmailTool.class)
                .addBeanClass(GoogleGmailConfig.class)
                .addBeanClass(GoogleGmailRestClient.class)
                .setRemovable()
                .build();
    }

    @BuildStep(onlyIf = IsGmailEnabled.class)
    ReflectiveClassBuildItem registerGmailReflection() {
        return ReflectiveClassBuildItem.builder(
                GmailTool.class.getName(),
                GoogleGmailConfig.class.getName(),
                GmailMessageList.class.getName(),
                GmailMessageRef.class.getName(),
                GmailMessage.class.getName(),
                GmailMessage.GmailMessagePartHeader.class.getName(),
                GmailSendRequest.class.getName()
        ).methods().fields().build();
    }
}
