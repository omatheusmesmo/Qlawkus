package dev.omatheusmesmo.qlawkus.store.pg.deployment;

import dev.omatheusmesmo.qlawkus.store.pg.devui.PgvectorDevUIJsonRPCService;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

/**
 * Contributes the pgvector extension's own card to the Dev UI, showing which implementation each
 * cognition SPI resolved to and offering the reconcile and migrate operations. Dev-mode only: a
 * production build produces no page and never wires the backing bean.
 */
class PgvectorDevUIProcessor {

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    AdditionalBeanBuildItem devUiBackingBean() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(PgvectorDevUIJsonRPCService.class)
                .setDefaultScope(DotNames.APPLICATION_SCOPED)
                .setUnremovable()
                .build();
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    JsonRPCProvidersBuildItem devUiJsonRpcService() {
        return new JsonRPCProvidersBuildItem(PgvectorDevUIJsonRPCService.class);
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    CardPageBuildItem devUiCard() {
        CardPageBuildItem card = new CardPageBuildItem();

        card.addPage(Page.webComponentPageBuilder()
                .title("Cognition backends")
                .icon("font-awesome-solid:database")
                .componentLink("qwc-qlawkus-pgvector-backends.js"));

        return card;
    }
}
