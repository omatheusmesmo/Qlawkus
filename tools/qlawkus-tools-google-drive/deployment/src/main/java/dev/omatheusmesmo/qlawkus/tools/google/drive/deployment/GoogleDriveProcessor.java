package dev.omatheusmesmo.qlawkus.tools.google.drive.deployment;

import dev.omatheusmesmo.qlawkus.tools.google.drive.DriveTool;
import dev.omatheusmesmo.qlawkus.tools.google.drive.GoogleDriveConfig;
import dev.omatheusmesmo.qlawkus.tools.google.drive.GoogleDriveRestClient;
import dev.omatheusmesmo.qlawkus.tools.google.drive.GoogleDriveDownloadClient;
import dev.omatheusmesmo.qlawkus.tools.google.drive.GoogleDriveUploadClient;
import dev.omatheusmesmo.qlawkus.tools.google.drive.model.DriveFile;
import dev.omatheusmesmo.qlawkus.tools.google.drive.model.DriveFileList;
import dev.omatheusmesmo.qlawkus.tools.google.drive.model.DrivePermission;
import dev.omatheusmesmo.qlawkus.tools.google.drive.model.DrivePermissionRequest;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

class GoogleDriveProcessor {

    private static final String FEATURE = "google-drive";
    private static final String REST_CLIENT_CAPABILITY = "io.quarkus.rest-client.jackson";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIf = IsDriveEnabled.class)
    AdditionalBeanBuildItem registerDriveBeans(Capabilities capabilities) {
        if (capabilities.isMissing(REST_CLIENT_CAPABILITY)) {
            throw new ConfigurationException(
                    "Drive tool requires quarkus-rest-client-jackson. "
                            + "Add the extension or disable drive with qlawkus.google.drive.enabled=false");
        }
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(DriveTool.class)
                .addBeanClass(GoogleDriveConfig.class)
                .addBeanClass(GoogleDriveRestClient.class)
                .addBeanClass(GoogleDriveDownloadClient.class)
                .addBeanClass(GoogleDriveUploadClient.class)
                .setRemovable()
                .build();
    }

    @BuildStep(onlyIf = IsDriveEnabled.class)
    ReflectiveClassBuildItem registerDriveReflection() {
        return ReflectiveClassBuildItem.builder(
                DriveTool.class.getName(),
                GoogleDriveConfig.class.getName(),
                GoogleDriveDownloadClient.class.getName(),
                GoogleDriveUploadClient.class.getName(),
                DriveFileList.class.getName(),
                DriveFile.class.getName(),
                DrivePermission.class.getName(),
                DrivePermissionRequest.class.getName()
        ).methods().fields().build();
    }
}
