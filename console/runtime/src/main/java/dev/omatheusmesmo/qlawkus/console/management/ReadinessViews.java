package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.logging.FailureSummary;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Turns readiness checks into the rows of the Health card. A check publishes free-form data, so only
 * three things are ever read from it: an {@code error} entry (or one ending in {@code Error}), the
 * {@code degraded} store list and a count of pending migrations. Every other entry is dropped, which
 * is what keeps a token or an address a check happens to carry off the page. The reason is cut to one
 * bounded line through the same redaction the logs use.
 *
 * <p>A check that throws is a row of its own, shown {@code DOWN} with the failure, never a reason to
 * lose the other rows.
 */
final class ReadinessViews {

    static final String UP = "UP";
    static final String DEGRADED = "DEGRADED";
    static final String DOWN = "DOWN";

    private static final String NO_REASON = "The check reported down without saying why.";
    private static final String PROXY_SUFFIX = "_(ClientProxy|Subclass)$";
    private static final String PRODUCT_PREFIX = "qlawkus-";

    private ReadinessViews() {
    }

    static List<ReadinessView> of(Iterable<HealthCheck> checks, UnaryOperator<String> redact) {
        List<ReadinessView> views = new ArrayList<>();
        for (HealthCheck check : checks) {
            views.add(view(check, redact));
        }
        views.sort(Comparator.comparing(ReadinessView::name));
        return views;
    }

    private static ReadinessView view(HealthCheck check, UnaryOperator<String> redact) {
        try {
            return describe(check.call(), redact);
        } catch (RuntimeException failure) {
            String name = check.getClass().getSimpleName().replaceFirst(PROXY_SUFFIX, "");
            FailureSummary summary = FailureSummary.of(failure, redact);
            return new ReadinessView(name, label(name), DOWN,
                    FailureSummary.boundedLine("The check itself failed: " + summary.error() + ": "
                            + summary.cause(), redact));
        }
    }

    private static ReadinessView describe(HealthCheckResponse response, UnaryOperator<String> redact) {
        String name = response.getName();
        Map<String, Object> data = new TreeMap<>(response.getData().orElse(Map.of()));
        if (response.getStatus() == HealthCheckResponse.Status.UP) {
            String degraded = degradedStores(data);
            if (degraded == null) {
                return new ReadinessView(name, label(name), UP, null);
            }
            return new ReadinessView(name, label(name), DEGRADED,
                    FailureSummary.boundedLine("Running, but not fully: " + degraded, redact));
        }
        return new ReadinessView(name, label(name), DOWN,
                FailureSummary.boundedLine(downReason(data), redact));
    }

    private static String degradedStores(Map<String, Object> data) {
        Object stores = data.get("degraded");
        if (stores == null || stores.toString().isBlank()) {
            return null;
        }
        return stores.toString().replace(",", ", ");
    }

    private static String downReason(Map<String, Object> data) {
        String errors = data.entrySet().stream()
                .filter(entry -> isErrorKey(entry.getKey()))
                .map(entry -> String.valueOf(entry.getValue()))
                .collect(Collectors.joining("; "));
        if (!errors.isBlank()) {
            return errors;
        }
        if (data.get("pending") instanceof Number pending && pending.intValue() > 0) {
            Object next = data.get("nextVersion");
            return pending + " database migration(s) pending" + (next == null ? "" : ", next " + next);
        }
        return NO_REASON;
    }

    private static boolean isErrorKey(String key) {
        return key.equals("error") || key.endsWith("Error");
    }

    private static String label(String name) {
        String plain = name.startsWith(PRODUCT_PREFIX) ? name.substring(PRODUCT_PREFIX.length()) : name;
        return plain.replace('-', ' ');
    }
}
