package dev.omatheusmesmo.qlawkus.console;

import dev.omatheusmesmo.qlawkus.composition.CompositionManifest;
import dev.omatheusmesmo.qlawkus.composition.CompositionManifestParser;
import dev.omatheusmesmo.qlawkus.composition.CompositionPaths;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Supplies what the console shows in its live status panel: the app version, the baked composition
 * posture (read once from the classpath {@code agent.yml} through the shared
 * {@link CompositionManifestParser}, so the console never re-implements manifest parsing), and the
 * current server time, which changes on every poll and thereby proves the HTMX refresh is live
 * rather than a one-shot render.
 */
@ApplicationScoped
public class ConsoleStatus {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String version;
    private final String posture;
    private final List<String> capabilities;

    public ConsoleStatus(
            @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev") String version) {
        this.version = version;
        CompositionManifest manifest = loadBakedManifest();
        this.posture = manifest == null ? "unknown" : manifest.buildTime().defaultPosture().wireName();
        this.capabilities = manifest == null ? List.of() : manifest.buildTime().except();
    }

    public Snapshot snapshot() {
        return new Snapshot(version, posture, capabilities, ZonedDateTime.now().format(TIME));
    }

    private static CompositionManifest loadBakedManifest() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(CompositionPaths.DEFAULT_MANIFEST)) {
            return in == null ? null : CompositionManifestParser.parse(in);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * An immutable view of the status panel's data. Fields are passed to the template individually,
     * so no reflective access to this record is needed at runtime (native-friendly).
     */
    public record Snapshot(String version, String posture, List<String> capabilities, String serverTime) {
    }
}
