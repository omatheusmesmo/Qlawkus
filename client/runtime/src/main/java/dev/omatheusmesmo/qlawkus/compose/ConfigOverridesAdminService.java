package dev.omatheusmesmo.qlawkus.compose;

import dev.omatheusmesmo.qlawkus.composition.CompositionPaths;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.config.InvalidConfigOverrideException;
import dev.omatheusmesmo.qlawkus.config.metadata.ConfigMetadataIndex;
import dev.omatheusmesmo.qlawkus.config.metadata.ConfigPropertyMetadata;
import dev.omatheusmesmo.qlawkus.dto.ConfigOverridesState;
import dev.omatheusmesmo.qlawkus.secrets.SecretPropertyCatalog;
import io.quarkus.runtime.annotations.ConfigPhase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Owns the config editor's {@code BUILD_TIME}/{@code BUILD_AND_RUN_TIME_FIXED} tier: the counterpart
 * of {@link CompositionAdminService} for arbitrary property values instead of capability selection.
 * Only validates and stages - never builds, mirroring the composition manifest's security boundary
 * (a compromised admin credential can at most stage a validated override set).
 *
 * <p>The staged file is deliberately separate from {@code agent.staged.yml}: the two tiers stay
 * parallel rather than merged into one schema, so the pom generator never has to grow a second
 * responsibility (reconciling {@code application.properties} lines from the manifest).
 */
@ApplicationScoped
public class ConfigOverridesAdminService {

    private static final String STAGED_FILE = "config.staged.properties";

    private final Path stagedPath;
    private final ConfigMetadataIndex metadataIndex;

    @Inject
    public ConfigOverridesAdminService(AgentConfig config, ConfigMetadataIndex metadataIndex) {
        this(Path.of(config.state().root(), STAGED_FILE), metadataIndex);
    }

    ConfigOverridesAdminService(Path stagedPath, ConfigMetadataIndex metadataIndex) {
        this.stagedPath = stagedPath;
        this.metadataIndex = metadataIndex;
    }

    /**
     * Validates {@code properties} (a {@code .properties}-formatted document) against the config
     * metadata and, only if every key is a documented, non-secret, {@code BUILD_TIME}/
     * {@code BUILD_AND_RUN_TIME_FIXED} property, stages it for the next rebuild. A rejected document
     * never touches disk.
     *
     * @throws InvalidConfigOverrideException if the text is malformed or names an out-of-scope property
     * @throws IOException if the staged file cannot be written
     */
    public void stage(String properties) throws IOException {
        validate(properties);
        if (stagedPath.getParent() != null) {
            Files.createDirectories(stagedPath.getParent());
        }
        Files.writeString(stagedPath, properties);
    }

    /** The active and staged config overrides, as the build/restart tooling reads them over the API. */
    public ConfigOverridesState currentState() throws IOException {
        return new ConfigOverridesState(activeOverrides(), stagedOverrides(), stagedAt());
    }

    /**
     * Discards any pending staged overrides.
     *
     * @return true if staged overrides were present and removed
     */
    public boolean discardStaged() throws IOException {
        return Files.deleteIfExists(stagedPath);
    }

    /**
     * Sets a single property in the staged overrides, read-modify-write on the whole document so
     * other already-staged properties are preserved. Starts from the currently staged document, or an
     * empty one if none is staged yet.
     *
     * @throws InvalidConfigOverrideException if the property is out of scope for this tier
     * @throws IOException if the staged file cannot be read or written
     */
    public void stageOne(String property, String value) throws IOException {
        Properties current = loadStagedProperties();
        current.setProperty(property, value);
        stage(render(current));
    }

    /**
     * Removes a single property from the staged overrides, read-modify-write on the whole document.
     *
     * @return true if the property was present in the staged document and removed
     */
    public boolean discardOne(String property) throws IOException {
        Properties current = loadStagedProperties();
        if (current.remove(property) == null) {
            return false;
        }
        if (current.isEmpty()) {
            return discardStaged();
        }
        try {
            stage(render(current));
        } catch (InvalidConfigOverrideException e) {
            // Removing a key can only shrink the document, never introduce a new violation.
            throw new IllegalStateException(e);
        }
        return true;
    }

    private Properties loadStagedProperties() throws IOException {
        Properties current = new Properties();
        String staged = stagedOverrides();
        if (staged != null) {
            try (StringReader reader = new StringReader(staged)) {
                current.load(reader);
            }
        }
        return current;
    }

    private static String render(Properties properties) throws IOException {
        StringWriter writer = new StringWriter();
        properties.store(writer, null);
        return writer.toString();
    }

    private void validate(String properties) {
        if (properties == null || properties.isBlank()) {
            throw new InvalidConfigOverrideException("request body must be a .properties document");
        }
        Properties parsed = new Properties();
        try (StringReader reader = new StringReader(properties)) {
            parsed.load(reader);
        } catch (IOException e) {
            throw new InvalidConfigOverrideException("Invalid .properties document: " + e.getMessage());
        }
        for (String key : parsed.stringPropertyNames()) {
            if (SecretPropertyCatalog.isSecret(key)) {
                throw new InvalidConfigOverrideException(
                        "%s is a secret; store it via PUT /api/admin/secrets instead".formatted(key));
            }
            ConfigPropertyMetadata metadata = metadataIndex.find(key)
                    .orElseThrow(() -> new InvalidConfigOverrideException(
                            "%s is not a documented, in-scope property".formatted(key)));
            if (metadata.phase() == ConfigPhase.RUN_TIME) {
                throw new InvalidConfigOverrideException(
                        "%s is a RUN_TIME property; set it via PUT /api/admin/runtime-toggles instead"
                                .formatted(key));
            }
        }
    }

    private String activeOverrides() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(CompositionPaths.DEFAULT_CONFIG_OVERRIDES)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private String stagedOverrides() throws IOException {
        return Files.exists(stagedPath) ? Files.readString(stagedPath) : null;
    }

    private String stagedAt() throws IOException {
        return Files.exists(stagedPath)
                ? Files.getLastModifiedTime(stagedPath).toInstant().toString()
                : null;
    }
}
