package dev.omatheusmesmo.qlawkus.console;

import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Builds the overview's Observability links from the configured Grafana base URL.
 *
 * <p>Prometheus, Tempo and Loki are only reached through Grafana (the bundled stack publishes nothing
 * else), so every link is a Grafana page: the Qlawkus dashboard and Explore on the three datasources
 * the {@code grafana/otel-lgtm} image provisions under the uids {@code prometheus}, {@code tempo} and
 * {@code loki}. A value that is not a plain http(s) URL yields no links rather than an unsafe href,
 * and is reported once without echoing it, since a URL can carry credentials.
 */
public final class ObservabilityLinks {

    static final String DASHBOARD_UID = "qlawkus-agent";

    private static final Logger LOG = Logger.getLogger(ObservabilityLinks.class);
    private static final String EXPLORE_PANES = "{\"a\":{\"datasource\":\"%1$s\",\"queries\":[{\"refId\":\"A\","
            + "\"datasource\":{\"type\":\"%1$s\",\"uid\":\"%1$s\"}}],\"range\":{\"from\":\"now-1h\",\"to\":\"now\"}}}";

    private ObservabilityLinks() {
    }

    public static List<ObservabilityLink> from(Optional<String> grafanaUrl) {
        return grafanaUrl
                .map(String::strip)
                .filter(url -> !url.isEmpty())
                .flatMap(ObservabilityLinks::plainHttpBase)
                .map(ObservabilityLinks::linksFor)
                .orElse(List.of());
    }

    private static List<ObservabilityLink> linksFor(String base) {
        return List.of(
                new ObservabilityLink("Qlawkus dashboard", "The agent's reference Grafana dashboard",
                        base + "/d/" + DASHBOARD_UID),
                new ObservabilityLink("Metrics", "Query Prometheus in Grafana Explore", explore(base, "prometheus")),
                new ObservabilityLink("Traces", "Search Tempo in Grafana Explore", explore(base, "tempo")),
                new ObservabilityLink("Logs", "Search Loki in Grafana Explore", explore(base, "loki")));
    }

    private static String explore(String base, String datasource) {
        String panes = URLEncoder.encode(EXPLORE_PANES.formatted(datasource), StandardCharsets.UTF_8);
        return base + "/explore?schemaVersion=1&panes=" + panes + "&orgId=1";
    }

    private static Optional<String> plainHttpBase(String configured) {
        try {
            URI uri = new URI(configured);
            boolean http = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            boolean plain = uri.getHost() != null && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null;
            if (http && plain) {
                return Optional.of(stripTrailingSlashes(configured));
            }
        } catch (URISyntaxException e) {
            LOG.debug("The configured Grafana URL does not parse", e);
        }
        LOG.warn("qlawkus.console.grafana-url is not a plain http(s) URL without credentials, query or "
                + "fragment; the console shows no observability links.");
        return Optional.empty();
    }

    private static String stripTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }
}
