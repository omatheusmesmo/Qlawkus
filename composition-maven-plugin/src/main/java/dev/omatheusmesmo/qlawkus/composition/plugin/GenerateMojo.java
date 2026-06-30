package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.omatheusmesmo.qlawkus.composition.CompositionManifest;
import dev.omatheusmesmo.qlawkus.composition.CompositionManifestParser;
import dev.omatheusmesmo.qlawkus.composition.CompositionPaths;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Generates the application pom dependency block from {@code agent.yml}, before the build. Runs as a
 * separate step (not a {@code @BuildStep}): Quarkus resolves dependencies before augmentation, so
 * the only place a manifest can decide the dependencies is ahead of the build. Invoke with
 * {@code mvn qlawkus:generate}.
 *
 * <p>The step reads the manifest, resolves the selected capabilities against the catalog, and
 * reconciles the pom's dependency block in place - adding selected capabilities, removing deselected
 * ones, and leaving the rest of the pom (the skeleton) untouched. It is idempotent: a pom already in
 * sync is not rewritten.
 */
@Mojo(name = "generate", requiresProject = true, threadSafe = true)
public class GenerateMojo extends AbstractMojo {

    /**
     * Location of the composition manifest. Defaults to {@code src/main/resources/qlawkus/agent.yml}
     * under the project base directory.
     */
    @Parameter(property = "qlawkus.composition.manifest")
    File manifest;

    /**
     * The pom to reconcile. Defaults to the project's own {@code pom.xml}.
     */
    @Parameter(defaultValue = "${project.file}", readonly = true, required = true)
    File pom;

    /**
     * Base directory of the project being generated. Bound by Maven; used to resolve the default
     * manifest location.
     */
    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    File basedir;

    private CapabilityCatalog catalog = List::of;

    @Override
    public void execute() throws MojoExecutionException {
        Path manifestPath = manifest != null
                ? manifest.toPath()
                : basedir.toPath().resolve("src/main/resources").resolve(CompositionPaths.DEFAULT_MANIFEST);

        if (!Files.isRegularFile(manifestPath)) {
            getLog().info("No composition manifest at " + manifestPath + "; nothing to generate.");
            return;
        }

        CompositionManifest parsed = CompositionManifestParser.parse(manifestPath);
        Resolution resolution = new CapabilityResolver(catalog).resolve(parsed.buildTime());
        report(manifestPath, resolution);

        reconcilePom(resolution);
    }

    private void reconcilePom(Resolution resolution) throws MojoExecutionException {
        Model model = readPom();
        if (PomComposer.compose(model, resolution)) {
            writePom(model);
            getLog().info("Updated " + pom + " dependency block from the manifest.");
        } else {
            getLog().info(pom + " already matches the manifest; no change.");
        }
    }

    private Model readPom() throws MojoExecutionException {
        try (Reader reader = Files.newBufferedReader(pom.toPath())) {
            return new MavenXpp3Reader().read(reader);
        } catch (IOException | XmlPullParserException e) {
            throw new MojoExecutionException("Cannot read pom " + pom + ": " + e.getMessage(), e);
        }
    }

    private void writePom(Model model) throws MojoExecutionException {
        try (Writer writer = Files.newBufferedWriter(pom.toPath())) {
            new MavenXpp3Writer().write(writer, model);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot write pom " + pom + ": " + e.getMessage(), e);
        }
    }

    private void report(Path manifestPath, Resolution resolution) {
        getLog().info("Composition manifest: " + manifestPath);
        getLog().info("  selected " + resolution.selected().size()
                + ", excluded " + resolution.excluded().size());
        resolution.selected().forEach(c -> getLog().info("  + " + c.name() + " (" + c.coordinates() + ")"));
        resolution.excluded().forEach(c -> getLog().info("  - " + c.name() + " (" + c.coordinates() + ")"));
        resolution.unknownExcept().forEach(name ->
                getLog().warn("  except '" + name + "' matches no known capability"));

        if (resolution.selected().isEmpty() && resolution.excluded().isEmpty()) {
            getLog().warn("Capability catalog is empty; nothing to reconcile (the catalog is issue #74).");
        }
    }

    void setCatalog(CapabilityCatalog catalog) {
        this.catalog = catalog;
    }

    void setPom(File pom) {
        this.pom = pom;
    }
}
