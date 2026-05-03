package dev.omatheusmesmo.qlawkus.tools.google.auth.deployment;

import dev.omatheusmesmo.qlawkus.tools.google.auth.CredentialVaultService;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleAuthResource;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleCredential;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleVaultConfig;
import dev.omatheusmesmo.qlawkus.tools.google.auth.DeviceCodeResponse;
import dev.omatheusmesmo.qlawkus.tools.google.auth.TokenResponse;
import dev.omatheusmesmo.qlawkus.tools.google.auth.RefreshTokenCapturedEvent;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleAuthConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageSecurityProviderBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

class GoogleAuthProcessor {

    private static final String FEATURE = "google-auth";
    private static final String HIBERNATE_PANACHE_CAPABILITY = "io.quarkus.hibernate-orm-panache";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem registerAuthBeans() {
        return AdditionalBeanBuildItem.builder()
            .addBeanClass(GoogleAuthResource.class)
            .addBeanClass(GoogleCredential.class)
            .setUnremovable()
            .build();
    }

    @BuildStep
    ReflectiveClassBuildItem registerAuthReflection() {
        return ReflectiveClassBuildItem.builder(
            DeviceCodeResponse.class.getName(),
            TokenResponse.class.getName(),
            RefreshTokenCapturedEvent.class.getName(),
            GoogleAuthConfig.class.getName(),
            GoogleCredential.class.getName()
        ).methods().fields().build();
    }

    @BuildStep(onlyIf = IsVaultEnabled.class)
    AdditionalBeanBuildItem registerVaultBeans(Capabilities capabilities) {
        if (capabilities.isMissing(HIBERNATE_PANACHE_CAPABILITY)) {
            throw new ConfigurationException(
                "Google vault requires quarkus-hibernate-orm-panache. "
                + "Add the extension or disable the vault with qlawkus.google.vault.enabled=false");
        }
        try {
            Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider", false,
                Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException(
                "Google vault requires BouncyCastle (org.bouncycastle:bcprov-jdk18on). "
                + "Add the dependency or disable the vault with qlawkus.google.vault.enabled=false");
        }
        return AdditionalBeanBuildItem.builder()
            .addBeanClass(CredentialVaultService.class)
            .addBeanClass(GoogleVaultConfig.class)
            .setRemovable()
            .build();
    }

    @BuildStep(onlyIf = IsVaultEnabled.class)
    ReflectiveClassBuildItem registerVaultReflection() {
        return ReflectiveClassBuildItem.builder(
                GoogleVaultConfig.class.getName()
        ).methods().fields().build();
    }

    @BuildStep(onlyIf = IsVaultEnabled.class)
    NativeImageSecurityProviderBuildItem registerBouncyCastleProvider() {
        return new NativeImageSecurityProviderBuildItem("org.bouncycastle.jce.provider.BouncyCastleProvider");
    }
}
