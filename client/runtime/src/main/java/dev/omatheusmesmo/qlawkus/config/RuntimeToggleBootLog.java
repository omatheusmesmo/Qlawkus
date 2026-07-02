package dev.omatheusmesmo.qlawkus.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;

import dev.omatheusmesmo.qlawkus.composition.RuntimeToggles;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Logs the runtime-toggle overrides in effect at boot, so an operator can see which toggles the
 * external {@code agent.runtime.yml} contributed without inspecting the file. Reads the same override
 * file the config source does. Never fails boot: a missing or unreadable override is reported, not
 * raised.
 */
@ApplicationScoped
public class RuntimeToggleBootLog {

    private final RuntimeToggleConfig config;

    public RuntimeToggleBootLog(RuntimeToggleConfig config) {
        this.config = config;
    }

    void onStart(@Observes StartupEvent event) {
        if (!config.enabled()) {
            Log.debug("Runtime toggles: disabled (qlawkus.composition.runtime.enabled=false).");
            return;
        }
        Path file = Path.of(expandHome(config.overridePath()));
        if (!Files.isRegularFile(file)) {
            Log.infof("Runtime toggles: no external override at %s; baked-in defaults apply.", file);
            return;
        }
        try {
            Map<String, String> override = RuntimeToggles.parseOverride(Files.readString(file));
            if (override.isEmpty()) {
                Log.infof("Runtime toggles: external override %s is empty.", file);
            } else {
                Log.infof("Runtime toggles: %d override(s) in effect from %s: %s (env and -D still win).",
                        override.size(), file, new TreeSet<>(override.keySet()));
            }
        } catch (Exception e) {
            Log.warnf("Runtime toggles: could not read override %s: %s", file, e.getMessage());
        }
    }

    private static String expandHome(String path) {
        return path.startsWith("~") ? System.getProperty("user.home") + path.substring(1) : path;
    }
}
