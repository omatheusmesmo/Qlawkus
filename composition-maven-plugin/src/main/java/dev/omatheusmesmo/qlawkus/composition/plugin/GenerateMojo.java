package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.omatheusmesmo.qlawkus.composition.CompositionManifest;
import dev.omatheusmesmo.qlawkus.composition.CompositionManifestParser;
import dev.omatheusmesmo.qlawkus.composition.CompositionPaths;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
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
 *
 * <p>The default binding is {@code generate-sources}, so an {@code <execution>} in the app pom lets
 * {@code quarkus:dev} regenerate the pom whenever {@code agent.yml} (which lives under
 * {@code src/main/resources}, already watched by dev mode) changes; Quarkus then sees the pom change
 * and restarts with the new capability set.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresProject = true, threadSafe = true)
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

    /**
     * All projects in the reactor. The default catalog is built from these: each module that
     * self-describes a {@code metadata.qlawkus.capability} in its {@code quarkus-extension.yaml}
     * contributes one capability.
     */
    @Parameter(defaultValue = "${reactorProjects}", readonly = true, required = true)
    List<MavenProject> reactorProjects;

    /**
     * Whether an {@code except} entry that names no known capability fails the build. Defaults to
     * {@code true}: a typo or a forgotten {@code metadata.qlawkus.capability} declaration otherwise
     * drops the extension silently. Set to {@code false} to downgrade the mismatch to a warning (for
     * a partial reactor build where not every module is present).
     */
    @Parameter(property = "qlawkus.composition.fail-on-unknown-capability", defaultValue = "true")
    boolean failOnUnknownCapability;

    private CapabilityCatalog catalog;

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
        Resolution resolution = new CapabilityResolver(effectiveCatalog()).resolve(parsed.buildTime());
        report(manifestPath, resolution);
        validate(resolution);

        reconcilePom(resolution);
    }

    /**
     * Fails the build (or warns, per {@code failOnUnknownCapability}) when an {@code except} entry
     * names no known capability. Skipped entirely when the catalog is empty: a single-module or
     * partial build (for example the reference app built on its own, without the reactor's extension
     * modules) cannot resolve any capability, so the guard has nothing to check against and would only
     * produce false positives. {@link #report} already warns about the empty catalog in that case.
     */
    private void validate(Resolution resolution) throws MojoExecutionException {
        if (resolution.unknownExcept().isEmpty() || resolution.catalogWasEmpty()) {
            return;
        }
        String names = String.join(", ", resolution.unknownExcept());
        if (failOnUnknownCapability) {
            throw new MojoExecutionException("Composition manifest lists 'except' entries that match no"
                    + " known capability: " + names + ". Fix the name or declare"
                    + " metadata.qlawkus.capability on the providing module. Set"
                    + " -Dqlawkus.composition.fail-on-unknown-capability=false to downgrade to a warning.");
        }
        resolution.unknownExcept().forEach(name ->
                getLog().warn("except '" + name + "' matches no known capability"));
    }

    private CapabilityCatalog effectiveCatalog() {
        if (catalog != null) {
            return catalog;
        }
        List<ReactorModule> modules = new ArrayList<>();
        if (reactorProjects != null) {
            for (MavenProject project : reactorProjects) {
                Path descriptor = project.getBasedir().toPath()
                        .resolve("src/main/resources/META-INF/quarkus-extension.yaml");
                modules.add(new ReactorModule(project.getGroupId(), project.getArtifactId(), descriptor));
            }
        }
        return new ReactorCatalog(modules);
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
        getLog().info("Resolved capability set from " + manifestPath);
        getLog().info("  " + resolution.selected().size() + " selected, "
                + resolution.excluded().size() + " excluded");
        resolution.selected().forEach(c -> getLog().info("  + " + c.name() + " (" + c.coordinates() + ")"));
        resolution.excluded().forEach(c -> getLog().info("  - " + c.name() + " (" + c.coordinates() + ")"));

        if (resolution.selected().isEmpty() && resolution.excluded().isEmpty()) {
            getLog().warn("Capability catalog is empty; no module declares metadata.qlawkus.capability, "
                    + "so there is nothing to reconcile.");
        }
    }

    void setCatalog(CapabilityCatalog catalog) {
        this.catalog = catalog;
    }

    void setPom(File pom) {
        this.pom = pom;
    }
}
