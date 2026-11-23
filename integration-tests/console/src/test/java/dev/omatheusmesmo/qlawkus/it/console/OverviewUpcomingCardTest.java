package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;

import dev.omatheusmesmo.qlawkus.task.DynamicTaskScheduler;
import dev.omatheusmesmo.qlawkus.task.TaskDestination;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OverviewUpcomingCardTest {

    private static final Pattern AT = Pattern.compile("data-at=\"([^\"]+)\"");
    private static final Pattern ROW = Pattern.compile("class=\"upcoming-row\"");

    @Inject
    DynamicTaskScheduler tasks;

    @Test
    void theOverviewLoadsTheCardAndPollsOnlyWhileTheTabIsVisible() {
        String page = get("/console");

        assertThat(page, containsString("hx-get=\"/console/overview/upcoming\""));
        assertThat(page, containsString("every 30s [document.visibilityState=='visible']"));
    }

    @Test
    void theCardAsksForAuthentication() {
        given().get("/console/overview/upcoming").then().statusCode(401);
    }

    @Test
    void onlyTheNextThreeRunsAreShownEarliestFirst() {
        String card = get("/console/overview/upcoming");

        Matcher rows = ROW.matcher(card);
        int count = 0;
        while (rows.find()) {
            count++;
        }
        assertThat(count, equalTo(3));

        List<Instant> times = new ArrayList<>();
        Matcher at = AT.matcher(card);
        while (at.find()) {
            times.add(Instant.parse(at.group(1)));
        }
        assertThat(times, hasSize(3));
        assertThat(times.stream().sorted().toList(), equalTo(times));
    }

    @Test
    void theCardLinksToTheSchedulePage() {
        assertThat(get("/console/overview/upcoming"), containsString("href=\"/console/schedule\""));
    }

    @Test
    void aBackgroundJobIsNamedInPlainWords() {
        String card = get("/console/overview/upcoming");

        assertThat(card, containsString("data-kind=\"job\""));
    }

    @Test
    void anAgentTaskDueSoonComesFirstAndIsEscaped() {
        var task = tasks.create("overview-upcoming", new TaskDestination("discord", "upcoming-chat"),
                "Send the <agenda> summary", "0 * * * * ? *");
        try {
            String card = get("/console/overview/upcoming");

            assertThat(card, containsString("data-kind=\"task\""));
            assertThat(card, containsString("Send the &lt;agenda&gt; summary"));
            assertThat(card, not(containsString("<agenda>")));
            assertThat(card.indexOf("data-kind=\"task\""), lessThan(card.indexOf("data-kind=\"job\"")));
        } finally {
            tasks.cancel(task.task().id());
        }
    }

    @Test
    void aCancelledTaskLeavesTheCard() {
        var task = tasks.create("overview-upcoming", new TaskDestination("discord", "gone-chat"),
                "Water the plants", "0 * * * * ? *");
        tasks.cancel(task.task().id());

        assertThat(get("/console/overview/upcoming"), not(containsString("Water the plants")));
    }

    private static String get(String path) {
        return given().auth().preemptive().basic("qlawkus", "qlawkus-test").get(path)
                .then().statusCode(200).extract().asString();
    }
}
