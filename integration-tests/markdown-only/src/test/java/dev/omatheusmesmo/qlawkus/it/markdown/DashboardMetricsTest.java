package dev.omatheusmesmo.qlawkus.it.markdown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.testing.QlawkusWireMockStubs;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Keeps the versioned Grafana dashboard honest against the metrics the agent really publishes. A
 * dashboard is a contract like a metric name is: a panel querying a series that was renamed does not
 * fail, it just goes quiet, and nobody notices until the day it was needed.
 *
 * <p>Two directions are checked. Every series a panel queries must appear in a real scrape, so a
 * renamed meter or a wrong suffix breaks the build instead of a panel. And every Qlawkus meter must
 * be charted somewhere, so a new one cannot ship without the integrator seeing it. The scrape is
 * taken after one real turn, for the upstream meters, and after calling each {@link AgentMeters}
 * recording method once, because some meters only register on first use: a fallback, say, never
 * happens on a healthy turn.
 */
@QuarkusTest
@ConnectWireMock
class DashboardMetricsTest {

    private static final Path DASHBOARD = Path.of("..", "..", "examples", "observability", "grafana",
            "qlawkus-agent.json");
    private static final Pattern SERIES = Pattern.compile("\\b(?:qlawkus|gen_ai|langchain4j)_[a-z0-9_]+\\b");
    private static final Pattern SUFFIX = Pattern.compile("(_seconds)?(_total|_count|_sum|_max)?$");

    WireMock wiremock;

    @Inject
    AgentService agentService;

    @Inject
    AgentMeters meters;

    @Test
    void everySeriesAPanelQueriesIsPublished() throws IOException {
        Set<String> published = scrapeAfterExercisingEveryMeter();

        Set<String> missing = new TreeSet<>(queriedSeries());
        missing.removeAll(published);

        assertEquals(Set.of(), missing,
                "these panels would sit empty, since the agent publishes no such series");
    }

    @Test
    void everyQlawkusMeterIsCharted() throws IOException {
        Set<String> charted = queriedSeries().stream()
                .map(series -> SUFFIX.matcher(series).replaceFirst("").replace('_', '.'))
                .collect(Collectors.toSet());

        Set<String> uncharted = new TreeSet<>(qlawkusMeterNames());
        uncharted.removeAll(charted);

        assertEquals(Set.of(), uncharted, "a meter nobody charts is one the integrator never sees");
    }

    private Set<String> queriedSeries() throws IOException {
        JsonNode dashboard = new ObjectMapper().readTree(DASHBOARD.toFile());
        Set<String> series = new TreeSet<>();
        for (JsonNode expr : dashboard.findValues("expr")) {
            Matcher matcher = SERIES.matcher(expr.asText());
            while (matcher.find()) {
                series.add(matcher.group());
            }
        }
        assertFalse(series.isEmpty(), "the dashboard queries nothing, so there is nothing to check");
        return series;
    }

    private Set<String> scrapeAfterExercisingEveryMeter() {
        QlawkusWireMockStubs.registerOpenAiStubs(wiremock);
        agentService.chat("dashboard-metrics", "Say exactly: pong")
                .collect().asList().await().atMost(Duration.ofSeconds(30));
        Duration took = Duration.ofMillis(1);
        meters.retrieval(1, new double[] {0.8}, took);
        meters.embedFact(2, () -> { });
        meters.job("dashboard-probe", took, 1, true);
        meters.fallback(AgentMeters.SURFACE_CHAT);
        meters.tool("dashboard-probe", took, true);
        meters.store("FactStore", "markdown", "store", took, true);

        String scrape = given().when().get("/q/metrics").then().statusCode(200).extract().asString();
        return scrape.lines()
                .filter(line -> !line.startsWith("#") && !line.isBlank())
                .map(line -> line.split("[{ ]", 2)[0])
                .collect(Collectors.toSet());
    }

    /** The meter names {@link AgentMeters} writes down, read from its constants so none is forgotten. */
    private static Set<String> qlawkusMeterNames() {
        return Arrays.stream(AgentMeters.class.getFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == String.class)
                .map(DashboardMetricsTest::valueOf)
                .filter(name -> name.startsWith("qlawkus."))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String valueOf(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
