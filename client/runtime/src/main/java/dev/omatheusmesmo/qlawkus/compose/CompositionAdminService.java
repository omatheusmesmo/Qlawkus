package dev.omatheusmesmo.qlawkus.compose;

import dev.omatheusmesmo.qlawkus.composition.CompositionManifestParser;
import dev.omatheusmesmo.qlawkus.composition.CompositionPaths;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.dto.CompositionState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Owns the composition intent of the running app. It only reads and stages manifests; it never
 * builds anything (the app has no toolchain by design). A rebuild is carried out externally by the
 * builder that reads the staged manifest back over the API, so even a compromised admin credential
 * can at most stage a validated manifest, never execute a command.
 *
 * <p>The staged manifest lives inside the app's own persistent state ({@code state.root}), not a
 * shared volume: the seam to the outside is HTTP, not the filesystem.
 */
@ApplicationScoped
public class CompositionAdminService {

    private static final String STAGED_FILE = "agent.staged.yml";

    private final Path stagedPath;

    @Inject
    public CompositionAdminService(AgentConfig config) {
        this(Path.of(config.state().root(), STAGED_FILE));
    }

    CompositionAdminService(Path stagedPath) {
        this.stagedPath = stagedPath;
    }

    /**
     * Validates {@code yaml} against the manifest schema and, only if valid, stages it for the next
     * rebuild. A rejected manifest never touches disk.
     *
     * @throws dev.omatheusmesmo.qlawkus.composition.InvalidManifestException if the YAML is malformed
     *         or violates the schema
     * @throws IOException if the staged file cannot be written
     */
    public void stage(String yaml) throws IOException {
        CompositionManifestParser.parse(yaml);
        Files.createDirectories(stagedPath.getParent());
        Files.writeString(stagedPath, yaml);
    }

    /**
     * The active and staged manifests, as the build/restart tooling reads them over the API.
     */
    public CompositionState currentState() throws IOException {
        return new CompositionState(activeManifest(), stagedManifest(), stagedAt());
    }

    /**
     * Discards any pending staged manifest.
     *
     * @return true if a staged manifest was present and removed
     */
    public boolean discardStaged() throws IOException {
        return Files.deleteIfExists(stagedPath);
    }

    private String activeManifest() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(CompositionPaths.DEFAULT_MANIFEST)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private String stagedManifest() throws IOException {
        return Files.exists(stagedPath) ? Files.readString(stagedPath) : null;
    }

    private String stagedAt() throws IOException {
        return Files.exists(stagedPath)
                ? Files.getLastModifiedTime(stagedPath).toInstant().toString()
                : null;
    }
}
