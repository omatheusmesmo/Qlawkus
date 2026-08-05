package dev.omatheusmesmo.qlawkus.messaging.deployment;

import dev.omatheusmesmo.qlawkus.messaging.devui.MessagingDevUIJsonRPCService;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

/**
 * Contributes the messaging core's own card to the Dev UI. The adapters (Discord, Telegram, and the
 * rest) are separate extensions, but they all register through this module's registry, so one page
 * showing every adapter and whether it actually became active is more useful than a card per
 * adapter repeating the same table.
 */
class MessagingDevUIProcessor {

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    AdditionalBeanBuildItem devUiBackingBean() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(MessagingDevUIJsonRPCService.class)
                .setDefaultScope(DotNames.APPLICATION_SCOPED)
                .setUnremovable()
                .build();
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    JsonRPCProvidersBuildItem devUiJsonRpcService() {
        return new JsonRPCProvidersBuildItem(MessagingDevUIJsonRPCService.class);
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    CardPageBuildItem devUiCard() {
        CardPageBuildItem card = new CardPageBuildItem();

        card.addPage(Page.webComponentPageBuilder()
                .title("Providers")
                .icon("font-awesome-solid:comments")
                .componentLink("qwc-qlawkus-messaging-providers.js"));

        return card;
    }
}
