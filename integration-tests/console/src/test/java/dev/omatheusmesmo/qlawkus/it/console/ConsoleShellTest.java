package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ConsoleShellTest {

    @Test
    void navigationIsGroupedAndEveryEntryStaysInItsGroup() {
        String page = page("/console");

        int agent = page.indexOf("data-nav-group=\"agent\"");
        int knowledge = page.indexOf("data-nav-group=\"knowledge\"");
        int system = page.indexOf("data-nav-group=\"system\"");
        assertThat(agent, greaterThan(-1));
        assertThat(knowledge, greaterThan(agent));
        assertThat(system, greaterThan(knowledge));

        assertInside(page, "href=\"/console\"", agent, knowledge);
        assertInside(page, "href=\"/console/chat\"", agent, knowledge);
        assertInside(page, "href=\"/console/memory\"", knowledge, system);
        assertInside(page, "href=\"/console/skills\"", knowledge, system);
        assertInside(page, "href=\"/console/cognition\"", knowledge, system);
        assertInside(page, "href=\"/console/config\"", system, page.indexOf("</nav>"));
        assertInside(page, "href=\"/console/schedule\"", system, page.indexOf("</nav>"));
    }

    @Test
    void theCurrentPageIsMarkedForAssistiveTechnology() {
        page("/console/memory").lines().filter(line -> line.contains("href=\"/console/memory\""))
                .findFirst().ifPresentOrElse(
                        line -> assertThat(line, containsString("aria-current=\"page\"")),
                        () -> {
                            throw new AssertionError("The memory entry is missing from the navigation.");
                        });
        String overview = page("/console");
        assertThat(overview.indexOf("aria-current=\"page\""), greaterThan(-1));
        assertThat(overview.indexOf("aria-current=\"page\""), lessThan(overview.indexOf("data-nav-group=\"knowledge\"")));
    }

    @Test
    void theShellOffersASkipLinkAndAKeyboardOperableMenu() {
        String page = page("/console");

        assertThat(page, containsString("class=\"skip-link\" href=\"#main\""));
        assertThat(page, containsString("<main class=\"content\" id=\"main\""));
        assertThat(page, containsString("id=\"nav-toggle\""));
        assertThat(page, containsString("aria-controls=\"site-nav\""));
        assertThat(page, containsString("aria-expanded=\"false\""));
        assertThat(page, containsString("<nav class=\"nav\" id=\"site-nav\" aria-label=\"Console\""));
    }

    private static void assertInside(String page, String marker, int from, int to) {
        int at = page.indexOf(marker);
        assertThat(marker + " is missing", at, greaterThan(-1));
        assertThat(marker + " is outside its group", at, greaterThan(from));
        assertThat(marker + " is outside its group", at, lessThan(to));
    }

    private static String page(String path) {
        return given().auth().preemptive().basic("qlawkus", "qlawkus-test").get(path)
                .then().statusCode(200).extract().asString();
    }
}
