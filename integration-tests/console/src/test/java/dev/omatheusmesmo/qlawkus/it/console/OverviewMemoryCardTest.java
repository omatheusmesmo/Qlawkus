package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.Comparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OverviewMemoryCardTest {

    private static final LocalDate SEEDED = LocalDate.of(2001, 2, 3);

    @Inject
    EpisodicStore journals;

    @Inject
    SkillStore skills;

    @BeforeEach
    void seedOneJournal() {
        if (!journals.existsForDate(SEEDED)) {
            journals.storeEpisode(SEEDED, "A day the overview card test remembers.", 4);
        }
    }

    @Test
    void theOverviewLoadsTheCardAndPollsOnlyWhileTheTabIsVisible() {
        String page = get("/console");

        assertThat(page, containsString("hx-get=\"/console/overview/memory\""));
        assertThat(page, containsString("every 60s [document.visibilityState=='visible']"));
    }

    @Test
    void theCardAsksForAuthentication() {
        given().get("/console/overview/memory").then().statusCode(401);
    }

    @Test
    void theCardCountsTheJournalsTheStoreHolds() {
        assertThat(get("/console/overview/memory"),
                containsString("data-journals=\"" + journals.count() + "\""));
    }

    @Test
    void theCardNamesTheNewestJournalDay() {
        LocalDate newest = journals.listJournals().stream().map(JournalSummary::date)
                .max(Comparator.naturalOrder()).orElseThrow();

        assertThat(get("/console/overview/memory"), containsString("data-latest=\"" + newest + "\""));
    }

    @Test
    void theCardCountsTheSkillsInTheIndex() {
        assertThat(get("/console/overview/memory"), containsString("data-skills=\"" + skills.index().size() + "\""));
    }

    @Test
    void theCardLinksToTheMemoryAndSkillsPages() {
        String card = get("/console/overview/memory");

        assertThat(card, containsString("href=\"/console/skills\""));
    }

    @Test
    void theCardLinksToTheMemoryPage() {
        assertThat(get("/console/overview/memory"), containsString("href=\"/console/memory\""));
    }

    private static String get(String path) {
        return given().auth().preemptive().basic("qlawkus", "qlawkus-test").get(path)
                .then().statusCode(200).extract().asString();
    }
}
