package dev.omatheusmesmo.qlawkus.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObservabilityLinksTest {

    private static final Path DASHBOARD = Path.of("../../examples/observability/grafana/qlawkus-agent.json");

    @Test
    void nothingConfiguredYieldsNoLinks() {
        assertTrue(ObservabilityLinks.from(Optional.empty()).isEmpty());
        assertTrue(ObservabilityLinks.from(Optional.of("  ")).isEmpty());
    }

    @Test
    void aGrafanaUrlYieldsTheDashboardAndOneExplorePerBackend() {
        List<ObservabilityLink> links = ObservabilityLinks.from(Optional.of("http://localhost:3000"));

        assertEquals(List.of("Qlawkus dashboard", "Metrics", "Traces", "Logs"),
                links.stream().map(ObservabilityLink::label).toList());
        assertEquals("http://localhost:3000/d/qlawkus-agent", links.get(0).url());
    }

    @Test
    void exploreLinksSelectTheProvisionedDatasourceByUid() throws IOException {
        Map<String, ObservabilityLink> byLabel = ObservabilityLinks.from(Optional.of("http://localhost:3000"))
                .stream().collect(Collectors.toMap(ObservabilityLink::label, link -> link));

        assertExploreOn(byLabel.get("Metrics"), "prometheus");
        assertExploreOn(byLabel.get("Traces"), "tempo");
        assertExploreOn(byLabel.get("Logs"), "loki");
    }

    @Test
    void aTrailingSlashIsDroppedAndASubPathIsKept() {
        List<ObservabilityLink> links = ObservabilityLinks.from(Optional.of(" https://ops.example.com/grafana/ "));

        assertEquals("https://ops.example.com/grafana/d/qlawkus-agent", links.get(0).url());
        assertTrue(links.get(1).url().startsWith("https://ops.example.com/grafana/explore?"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JAVASCRIPT:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "ftp://grafana.example.com",
            "//grafana.example.com",
            "grafana.example.com:3000",
            "http://",
            "http://user:secret@grafana.example.com",
            "http://grafana.example.com/?next=x",
            "not a url"
    })
    void anythingThatIsNotAPlainHttpUrlIsRejected(String configured) {
        assertTrue(ObservabilityLinks.from(Optional.of(configured)).isEmpty(), configured);
    }

    @Test
    void theDashboardUidMatchesTheShippedDashboard() throws IOException {
        String uid = JsonMapper.builder().build().readTree(DASHBOARD.toFile()).path("uid").asString();

        assertEquals(uid, ObservabilityLinks.DASHBOARD_UID);
    }

    private static void assertExploreOn(ObservabilityLink link, String datasourceUid) throws IOException {
        URI uri = URI.create(link.url());
        assertEquals("/explore", uri.getPath());
        String query = uri.getRawQuery();
        assertTrue(query.startsWith("schemaVersion=1&panes="), query);
        String panes = URLDecoder.decode(query.substring("schemaVersion=1&panes=".length()).split("&")[0],
                StandardCharsets.UTF_8);
        var pane = JsonMapper.builder().build().readTree(panes).path("a");
        assertEquals(datasourceUid, pane.path("datasource").asString());
        assertEquals(datasourceUid, pane.path("queries").path(0).path("datasource").path("uid").asString());
    }
}
