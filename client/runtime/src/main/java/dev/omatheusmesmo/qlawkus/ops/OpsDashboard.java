package dev.omatheusmesmo.qlawkus.ops;

import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Plain HTML status on the management interface: registered tool names and a small slice of
 * {@code qlawkus.*} counters. Served beside {@code /q/metrics} on port 9000, with no auth.
 */
@ApplicationScoped
public class OpsDashboard {

    private final ToolCatalog catalog;
    private final Instance<MeterRegistry> registry;

    @Inject
    OpsDashboard(ToolCatalog catalog, Instance<MeterRegistry> registry) {
        this.catalog = catalog;
        this.registry = registry;
    }

    public String renderHtml() {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">");
        html.append("<title>Qlawkus ops</title>");
        html.append("<style>body{font-family:system-ui,sans-serif;margin:1.5rem;line-height:1.5}");
        html.append("table{border-collapse:collapse}td,th{border:1px solid #ccc;padding:.35rem .6rem;text-align:left}");
        html.append("ul{columns:3 12rem}</style></head><body>");
        html.append("<h1>Qlawkus ops</h1>");
        html.append("<p>Full charts live in Grafana when configured; raw series at ");
        html.append("<a href=\"/q/metrics\">/q/metrics</a> on the management port.</p>");

        html.append("<h2>Tools (").append(catalog.toolNames().size()).append(")</h2><ul>");
        for (String name : catalog.toolNames()) {
            html.append("<li>").append(escape(name)).append("</li>");
        }
        html.append("</ul>");

        html.append("<h2>Metrics</h2>");
        if (registry.isResolvable()) {
            html.append(toolInvocationTable(registry.get()));
        } else {
            html.append("<p>Telemetry extension not present; no in-process registry.</p>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    public void serve(RoutingContext ctx) {
        ctx.response().putHeader("Content-Type", "text/html; charset=utf-8").end(renderHtml());
    }

    private static String toolInvocationTable(MeterRegistry meters) {
        Map<String, Double> byTool = meters.find(AgentMeters.TOOL_INVOCATIONS).counters().stream()
                .collect(Collectors.toMap(
                        c -> c.getId().getTag("tool") != null ? c.getId().getTag("tool") : "(unknown)",
                        Counter::count,
                        Double::sum));
        if (byTool.isEmpty()) {
            return "<p>No tool invocations recorded yet.</p>";
        }
        StringBuilder table = new StringBuilder("<table><thead><tr><th>Tool</th><th>Invocations</th>"
                + "</tr></thead><tbody>");
        byTool.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> table.append("<tr><td>").append(escape(entry.getKey()))
                        .append("</td><td>").append(formatCount(entry.getValue())).append("</td></tr>"));
        table.append("</tbody></table>");
        return table.toString();
    }

    private static String formatCount(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
