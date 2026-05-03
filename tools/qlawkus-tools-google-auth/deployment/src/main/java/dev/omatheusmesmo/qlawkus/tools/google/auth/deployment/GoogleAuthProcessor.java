package dev.omatheusmesmo.qlawkus.tools.google.auth.deployment;

import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleAuthResource;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleAuthConfig;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleDeviceFlowClient;
import dev.omatheusmesmo.qlawkus.tools.google.auth.DeviceCodeResponse;
import dev.omatheusmesmo.qlawkus.tools.google.auth.TokenResponse;
import dev.omatheusmesmo.qlawkus.tools.google.auth.RefreshTokenCapturedEvent;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

class GoogleAuthProcessor {

    private static final String FEATURE = "google-auth";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem registerBeans() {
        return AdditionalBeanBuildItem.unremovableOf(GoogleAuthResource.class);
    }

    @BuildStep
    ReflectiveClassBuildItem registerReflection() {
        return ReflectiveClassBuildItem.builder(
                DeviceCodeResponse.class.getName(),
                TokenResponse.class.getName(),
                RefreshTokenCapturedEvent.class.getName(),
                GoogleAuthConfig.class.getName()
        ).methods().fields().build();
    }
}
