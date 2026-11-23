package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.Readiness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OverviewHealthCardTest {

    @Inject
    @Readiness
    ControllableReadiness readiness;

    @AfterEach
    void restore() {
        readiness.recover();
    }

    @Test
    void theOverviewLoadsTheCardAndPollsOnlyWhileTheTabIsVisible() {
        String page = card("/console");

        assertThat(page, containsString("hx-get=\"/console/overview/health\""));
        assertThat(page, containsString("every 10s [document.visibilityState=='visible']"));
    }

    @Test
    void everyReadinessCheckIsListedWithItsStatus() {
        String card = card("/console/overview/health");

        assertThat(card, containsString("data-check=\"qlawkus-cognition\""));
        assertThat(card, containsString("data-check=\"qlawkus-model\""));
        assertThat(card, containsString("data-check=\"" + ControllableReadiness.NAME + "\" data-status=\"UP\""));
    }

    @Test
    void aFailingCheckIsDownAndStatesItsReasonInPlainWords() {
        readiness.failWith("the vault is unreachable");

        String card = card("/console/overview/health");

        assertThat(card, containsString("data-check=\"" + ControllableReadiness.NAME + "\" data-status=\"DOWN\""));
        assertThat(card, containsString("the vault is unreachable"));
    }

    @Test
    void aCheckThatIsUpButNamesADegradedStoreIsShownAsDegraded() {
        readiness.degradeWith("journal");

        String card = card("/console/overview/health");

        assertThat(card, containsString("data-check=\"" + ControllableReadiness.NAME + "\" data-status=\"DEGRADED\""));
        assertThat(card, containsString("journal"));
    }

    @Test
    void dataThatIsNotAReasonIsNeverRendered() {
        readiness.failWith("the vault is unreachable");

        assertThat(card("/console/overview/health"), not(containsString(ControllableReadiness.NOT_A_REASON)));
    }

    private static String card(String path) {
        return given().auth().preemptive().basic("qlawkus", "qlawkus-test").get(path)
                .then().statusCode(200).extract().asString();
    }
}
