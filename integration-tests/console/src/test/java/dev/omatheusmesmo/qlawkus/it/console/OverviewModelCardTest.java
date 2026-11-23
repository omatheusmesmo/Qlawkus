package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.omatheusmesmo.qlawkus.model.PrimaryChatGuard;
import dev.omatheusmesmo.qlawkus.model.ServedTier;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OverviewModelCardTest {

    @Inject
    PrimaryChatGuard guard;

    @BeforeEach
    @AfterEach
    void clean() {
        CircuitBreakerMaintenance.get().resetAll();
        guard.call(() -> response("ok"), () -> response("fallback"));
        ServedTier.forgetLastServed();
    }

    @Test
    void theOverviewLoadsTheCardAndPollsOnlyWhileTheTabIsVisible() {
        String page = get("/console");

        assertThat(page, containsString("hx-get=\"/console/overview/model\""));
        assertThat(page, containsString("every 10s [document.visibilityState=='visible']"));
    }

    @Test
    void theCardAsksForAuthentication() {
        given().get("/console/overview/model").then().statusCode(401);
    }

    @Test
    void everyTierOfTheChainIsListedWithItsBreaker() {
        String card = get("/console/overview/model");

        assertThat(card, containsString("data-tier=\"primary\" data-state=\"CLOSED\""));
        assertThat(card, containsString("data-tier=\"secondary\""));
        assertThat(card, containsString("data-tier=\"fallback\""));
    }

    @Test
    void anOpenBreakerIsShownAsDegradedInPlainWords() {
        guard.call(() -> {
            throw new RuntimeException("primary is down");
        }, () -> response("fallback"));

        String card = get("/console/overview/model");

        assertThat(card, containsString("data-tier=\"primary\" data-state=\"OPEN\""));
        assertThat(card, containsString("is failing, so the next tier answers"));
    }

    @Test
    void aPermanentFailureIsNamedByItsStatusEvenThoughNoBreakerOpened() {
        for (int i = 0; i < 3; i++) {
            assertThrows(RuntimeException.class, () -> guard.call(() -> {
                throw new AuthenticationException(new HttpException(401, "revoked key"));
            }, () -> response("fallback")));
        }

        String card = get("/console/overview/model");

        assertThat(card, containsString("data-tier=\"primary\" data-state=\"CLOSED\""));
        assertThat(card, containsString("data-permanent=\"chat\""));
        assertThat(card, containsString("HTTP 401"));
        assertThat(card, containsString("rejected the credential"));
    }

    @Test
    void theTierThatAnsweredLastIsNamed() {
        ServedTier.answered(ServedTier.SECONDARY, response("answer"));

        assertThat(get("/console/overview/model"), containsString("data-last=\"secondary\""));
    }

    @Test
    void beforeAnyAnswerTheCardSaysNothingHasBeenServed() {
        String card = get("/console/overview/model");

        assertThat(card, containsString("No turn has been answered since the agent started."));
        assertThat(card, not(containsString("data-last=")));
    }

    private static ChatResponse response(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    private static String get(String path) {
        return given().auth().preemptive().basic("qlawkus", "qlawkus-test").get(path)
                .then().statusCode(200).extract().asString();
    }
}
