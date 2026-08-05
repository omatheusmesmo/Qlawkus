package dev.omatheusmesmo.qlawkus.console.deployment;

import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

/**
 * Contributes the console's card to the Dev UI. The console is already a full server-rendered admin
 * UI, so the card links out to it rather than duplicating any of its screens as Dev UI pages - the
 * useful thing here is the shortcut and the reminder that the pages are behind the app's own
 * authentication, which the Dev UI does not share.
 */
class ConsoleDevUIProcessor {

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    CardPageBuildItem devUiCard() {
        CardPageBuildItem card = new CardPageBuildItem();

        card.addPage(Page.externalPageBuilder("Open the console")
                .url("/console")
                .isHtmlContent()
                .doNotEmbed()
                .icon("font-awesome-solid:sliders"));

        card.addPage(Page.externalPageBuilder("Onboarding wizard")
                .url("/console/onboarding")
                .isHtmlContent()
                .doNotEmbed()
                .icon("font-awesome-solid:wand-magic-sparkles"));

        card.addPage(Page.externalPageBuilder("Configuration editor")
                .url("/console/config")
                .isHtmlContent()
                .doNotEmbed()
                .icon("font-awesome-solid:gears"));

        card.addPage(Page.externalPageBuilder("Schedule")
                .url("/console/schedule")
                .isHtmlContent()
                .doNotEmbed()
                .icon("font-awesome-solid:clock"));

        return card;
    }
}
