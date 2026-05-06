package dev.omatheusmesmo.qlawkus.tools.google.storage.deployment;

import dev.omatheusmesmo.qlawkus.tools.google.storage.GoogleStorageConfig;
import dev.omatheusmesmo.qlawkus.tools.google.storage.GoogleStorageDownloadClient;
import dev.omatheusmesmo.qlawkus.tools.google.storage.GoogleStorageRestClient;
import dev.omatheusmesmo.qlawkus.tools.google.storage.StorageTool;
import dev.omatheusmesmo.qlawkus.tools.google.storage.model.StorageBucket;
import dev.omatheusmesmo.qlawkus.tools.google.storage.model.StorageBucketList;
import dev.omatheusmesmo.qlawkus.tools.google.storage.model.StorageObject;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

class GoogleStorageProcessor {

    private static final String FEATURE = "google-storage";
    private static final String REST_CLIENT_CAPABILITY = "io.quarkus.rest-client.jackson";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIf = IsStorageEnabled.class)
    AdditionalBeanBuildItem registerStorageBeans(Capabilities capabilities) {
        if (capabilities.isMissing(REST_CLIENT_CAPABILITY)) {
            throw new ConfigurationException(
                    "Storage tool requires quarkus-rest-client-jackson. "
                            + "Add the extension or disable storage with qlawkus.google.storage.enabled=false");
        }
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(StorageTool.class)
                .addBeanClass(GoogleStorageConfig.class)
                .addBeanClass(GoogleStorageRestClient.class)
                .addBeanClass(GoogleStorageDownloadClient.class)
                .setRemovable()
                .build();
    }

    @BuildStep(onlyIf = IsStorageEnabled.class)
    ReflectiveClassBuildItem registerStorageReflection() {
        return ReflectiveClassBuildItem.builder(
                StorageTool.class.getName(),
                GoogleStorageConfig.class.getName(),
                GoogleStorageRestClient.class.getName(),
                GoogleStorageDownloadClient.class.getName(),
                StorageBucketList.class.getName(),
                StorageBucket.class.getName(),
                StorageObject.class.getName()
        ).methods().fields().build();
    }
}
