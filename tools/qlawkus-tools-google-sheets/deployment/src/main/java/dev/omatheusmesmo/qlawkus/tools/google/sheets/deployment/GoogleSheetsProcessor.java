package dev.omatheusmesmo.qlawkus.tools.google.sheets.deployment;

import dev.omatheusmesmo.qlawkus.tools.google.sheets.GoogleSheetsConfig;
import dev.omatheusmesmo.qlawkus.tools.google.sheets.GoogleSheetsRestClient;
import dev.omatheusmesmo.qlawkus.tools.google.sheets.SheetsTool;
import dev.omatheusmesmo.qlawkus.tools.google.sheets.model.SheetValues;
import dev.omatheusmesmo.qlawkus.tools.google.sheets.model.UpdateValuesRequest;
import dev.omatheusmesmo.qlawkus.tools.google.sheets.model.UpdateValuesResponse;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

class GoogleSheetsProcessor {

    private static final String FEATURE = "google-sheets";
    private static final String REST_CLIENT_CAPABILITY = "io.quarkus.rest-client.jackson";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIf = IsSheetsEnabled.class)
    AdditionalBeanBuildItem registerSheetsBeans(Capabilities capabilities) {
        if (capabilities.isMissing(REST_CLIENT_CAPABILITY)) {
            throw new ConfigurationException(
                    "Sheets tool requires quarkus-rest-client-jackson. "
                            + "Add the extension or disable sheets with qlawkus.google.sheets.enabled=false");
        }
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(SheetsTool.class)
                .addBeanClass(GoogleSheetsConfig.class)
                .addBeanClass(GoogleSheetsRestClient.class)
                .setRemovable()
                .build();
    }

    @BuildStep(onlyIf = IsSheetsEnabled.class)
    ReflectiveClassBuildItem registerSheetsReflection() {
        return ReflectiveClassBuildItem.builder(
                SheetsTool.class.getName(),
                GoogleSheetsConfig.class.getName(),
                GoogleSheetsRestClient.class.getName(),
                SheetValues.class.getName(),
                UpdateValuesRequest.class.getName(),
                UpdateValuesResponse.class.getName()
        ).methods().fields().build();
    }
}
