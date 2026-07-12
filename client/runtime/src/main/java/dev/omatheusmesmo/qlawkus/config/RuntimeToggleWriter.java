package dev.omatheusmesmo.qlawkus.config;

import dev.omatheusmesmo.qlawkus.composition.RuntimeToggles;
import dev.omatheusmesmo.qlawkus.config.metadata.ConfigMetadataIndex;
import dev.omatheusmesmo.qlawkus.config.metadata.ConfigPropertyMetadata;
import io.quarkus.runtime.annotations.ConfigPhase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes {@code RUN_TIME} properties into the runtime-toggle external override file
 * ({@code qlawkus.runtime.override-path}), the write-side counterpart of
 * {@code RuntimeToggleConfigSourceFactory}. A write here takes effect on the next restart, since
 * config sources are read once at startup - there is no live-apply within a running process.
 *
 * <p>Read-modify-write on the whole file: every call reads the current override map, patches one key,
 * and rewrites the file, so unrelated toggles already present are preserved.
 */
@ApplicationScoped
public class RuntimeToggleWriter {

    private final RuntimeToggleConfig config;
    private final ConfigMetadataIndex metadataIndex;

    @Inject
    public RuntimeToggleWriter(RuntimeToggleConfig config, ConfigMetadataIndex metadataIndex) {
        this.config = config;
        this.metadataIndex = metadataIndex;
    }

    /**
     * Sets {@code property} to {@code value} in the override file, creating the file (and its parent
     * directory) if it does not exist yet. Overwrites an existing entry with the same key.
     *
     * @throws IllegalArgumentException if the property is undocumented/out of scope, or is not
     *         {@code RUN_TIME}-phase - a build-time property written here would never be read, since
     *         this file only feeds a {@code ConfigSource} evaluated at boot
     */
    public void setToggle(String property, String value) {
        requireProperty(property);
        if (value == null) {
            throw new IllegalArgumentException("Toggle value must not be null");
        }
        ConfigPropertyMetadata metadata = metadataIndex.find(property)
                .orElseThrow(() -> new IllegalArgumentException(
                        "%s is not a documented, in-scope property".formatted(property)));
        if (metadata.phase() != ConfigPhase.RUN_TIME) {
            throw new IllegalArgumentException(
                    "%s is a %s property; set it via PUT /api/admin/config-overrides instead"
                            .formatted(property, metadata.phase()));
        }
        Map<String, String> toggles = readAll();
        toggles.put(property, value);
        writeAll(toggles);
    }

    /**
     * Removes {@code property} from the override file. Returns {@code true} if it was present and
     * removed, {@code false} if the property (or the file) was absent.
     */
    public boolean deleteToggle(String property) {
        requireProperty(property);
        Map<String, String> toggles = readAll();
        boolean removed = toggles.remove(property) != null;
        if (removed) {
            writeAll(toggles);
        }
        return removed;
    }

    /**
     * The full set of toggles currently persisted in the override file. Empty when the file does not
     * exist yet.
     */
    public Map<String, String> all() {
        return readAll();
    }

    private Map<String, String> readAll() {
        Path file = overridePath();
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(RuntimeToggles.parseOverride(Files.readString(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read runtime override file: " + file, e);
        }
    }

    private void writeAll(Map<String, String> toggles) {
        Path file = overridePath();
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, RuntimeToggles.renderOverride(toggles));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write runtime override file: " + file, e);
        }
    }

    private Path overridePath() {
        return Path.of(expandHome(config.overridePath()));
    }

    private static String expandHome(String path) {
        return path.startsWith("~") ? System.getProperty("user.home") + path.substring(1) : path;
    }

    private static void requireProperty(String property) {
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("Toggle property must not be blank");
        }
    }
}
