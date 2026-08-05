package dev.omatheusmesmo.qlawkus.tools.skillhub.deployment;

import dev.omatheusmesmo.qlawkus.tools.skillhub.devui.SkillHubDevUIJsonRPCService;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

/**
 * Contributes the Skill Hub's own card to the Dev UI: search the remote registry and install a
 * skill straight into the owned root. Dev-mode only.
 */
class SkillHubDevUIProcessor {

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    AdditionalBeanBuildItem devUiBackingBean() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(SkillHubDevUIJsonRPCService.class)
                .setDefaultScope(DotNames.APPLICATION_SCOPED)
                .setUnremovable()
                .build();
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    JsonRPCProvidersBuildItem devUiJsonRpcService() {
        return new JsonRPCProvidersBuildItem(SkillHubDevUIJsonRPCService.class);
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    CardPageBuildItem devUiCard() {
        CardPageBuildItem card = new CardPageBuildItem();

        card.addPage(Page.webComponentPageBuilder()
                .title("Browse the hub")
                .icon("font-awesome-solid:magnifying-glass")
                .componentLink("qwc-qlawkus-skill-hub.js"));

        return card;
    }
}
