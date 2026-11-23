package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A card whose source cannot be read says so in its own box and leaves every other card alone. With
 * the HTMX default a failed poll would keep showing the last good numbers, which is the worst thing
 * a health page can do, so the endpoint answers 200 with an error fragment instead.
 */
@QuarkusTest
@TestProfile(OverviewErrorStateTest.Unreadable.class)
class OverviewErrorStateTest {

    public static class Unreadable implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(FailingEpisodicStore.class);
        }
    }

    @Test
    void anUnreadableSourceShowsAnErrorStateWithTheReasonInPlainWords() {
        String card = get("/console/overview/memory");

        assertThat(card, containsString("data-card-error"));
        assertThat(card, containsString("could not be read"));
        assertThat(card, containsString("the journal volume is unreadable"));
    }

    @Test
    void theOtherCardsKeepWorking() {
        assertThat(get("/console/overview/model"), containsString("data-tier=\"primary\""));
        assertThat(get("/console/overview/upcoming"), not(containsString("data-card-error")));
        assertThat(get("/console/overview/health"), not(containsString("data-card-error")));
    }

    private static String get(String path) {
        return given().auth().preemptive().basic("qlawkus", "qlawkus-test").get(path)
                .then().statusCode(200).extract().asString();
    }
}
