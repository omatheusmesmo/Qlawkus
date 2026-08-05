package dev.omatheusmesmo.qlawkus.devui;

import dev.omatheusmesmo.qlawkus.composition.BuildTime;
import dev.omatheusmesmo.qlawkus.composition.CompositionManifest;
import dev.omatheusmesmo.qlawkus.composition.CompositionManifestParser;
import dev.omatheusmesmo.qlawkus.composition.Posture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Read/write access to the application's own {@code agent.yml}, pointing at the file under
 * {@code src/main/resources} rather than the copy in {@code target/classes}. Writing the source is
 * what makes a capability toggle stick: dev mode watches the source tree, so the edit re-runs the
 * pom generator and reloads, while an edit to the build output would be silently discarded on the
 * next build.
 *
 * <p>Only ever constructed by the Dev UI build step, which runs in local dev mode. The path is null
 * when the application has no workspace module (a built artifact rather than a source checkout), and
 * every mutating call then refuses rather than guessing a location.
 */
public class DevUiCompositionSource {

    private final Path manifest;

    public DevUiCompositionSource(Path manifest) {
        this.manifest = manifest;
    }

    public boolean isWritable() {
        return manifest != null && Files.isRegularFile(manifest);
    }

    public String location() {
        return manifest == null ? "" : manifest.toString();
    }

    public CompositionManifest read() {
        if (!isWritable()) {
            throw new IllegalStateException("No writable agent.yml found for this application");
        }
        return CompositionManifestParser.parse(manifest);
    }

    /**
     * Flips one capability and rewrites the manifest, preserving the declared posture. The
     * {@code except} list always carries the opposite effect of the posture, so the same request
     * means "add to except" under a disabled default and "remove from except" under an enabled one.
     *
     * @return the manifest as written
     */
    public CompositionManifest setCapability(String capability, boolean enabled) {
        if (capability == null || capability.isBlank()) {
            throw new IllegalArgumentException("Capability name must not be blank");
        }
        CompositionManifest current = read();
        BuildTime buildTime = current.buildTime();

        boolean shouldBeListed = buildTime.defaultPosture() == Posture.ENABLED ? !enabled : enabled;
        List<String> except = new ArrayList<>(buildTime.except());
        if (shouldBeListed) {
            if (!except.contains(capability)) {
                except.add(capability);
            }
        } else {
            except.remove(capability);
        }
        except.sort(String::compareTo);

        CompositionManifest updated = new CompositionManifest(
                current.version(),
                new BuildTime(buildTime.defaultPosture(), except),
                current.runtime());
        write(updated);
        return updated;
    }

    /** Replaces the manifest wholesale, after validating it parses. */
    public CompositionManifest replace(String yaml) {
        CompositionManifest parsed = CompositionManifestParser.parse(yaml);
        write(parsed);
        return parsed;
    }

    /**
     * Rewrites the manifest, carrying the file's header comment across. The renderer works from the
     * parsed model and so cannot know about comments, and the manifest it is rewriting is a
     * hand-authored file whose header usually explains the composition policy - silently deleting
     * that on the first toggle would be a poor trade for a one-line edit.
     *
     * <p>Only the leading comment block survives; comments further down, interleaved with the
     * entries, are still lost. Preserving those would mean editing the YAML as text rather than as a
     * model, which trades a rare convenience for a parser this code deliberately does not own.
     */
    private void write(CompositionManifest updated) {
        try {
            Files.writeString(manifest, header() + CompositionManifestParser.render(updated));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write " + manifest + ": " + e.getMessage(), e);
        }
    }

    private String header() {
        try {
            StringBuilder header = new StringBuilder();
            for (String line : Files.readAllLines(manifest)) {
                if (!line.startsWith("#") && !line.isBlank()) {
                    break;
                }
                header.append(line).append(System.lineSeparator());
            }
            return header.toString();
        } catch (IOException e) {
            return "";
        }
    }
}
