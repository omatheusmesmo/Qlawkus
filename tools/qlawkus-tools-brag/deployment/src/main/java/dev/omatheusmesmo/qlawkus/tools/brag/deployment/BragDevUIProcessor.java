package dev.omatheusmesmo.qlawkus.tools.brag.deployment;

import dev.omatheusmesmo.qlawkus.tools.brag.devui.BragDevUIJsonRPCService;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

/**
 * Contributes the Brag tool's own card to the Dev UI: the recorded achievements, plus a link to the
 * markdown export the tool already serves. Dev-mode only.
 */
class BragDevUIProcessor {

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    AdditionalBeanBuildItem devUiBackingBean() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(BragDevUIJsonRPCService.class)
                .setDefaultScope(DotNames.APPLICATION_SCOPED)
                .setUnremovable()
                .build();
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    JsonRPCProvidersBuildItem devUiJsonRpcService() {
        return new JsonRPCProvidersBuildItem(BragDevUIJsonRPCService.class);
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    CardPageBuildItem devUiCard() {
        CardPageBuildItem card = new CardPageBuildItem();

        card.addPage(Page.webComponentPageBuilder()
                .title("Achievements")
                .icon("font-awesome-solid:trophy")
                .componentLink("qwc-qlawkus-brag.js"));

        card.addPage(Page.externalPageBuilder("Markdown export")
                .url("/api/brag/export")
                .doNotEmbed()
                .icon("font-awesome-solid:file-arrow-down"));

        return card;
    }
}
