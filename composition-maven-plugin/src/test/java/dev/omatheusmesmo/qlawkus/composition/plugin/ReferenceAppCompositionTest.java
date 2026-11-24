package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import dev.omatheusmesmo.qlawkus.composition.CompositionManifestParser;
import dev.omatheusmesmo.qlawkus.composition.CompositionPaths;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the reference distribution: {@code app/pom.xml} must already match what {@code agent.yml}
 * would generate against the full reactor catalog.
 */
class ReferenceAppCompositionTest {

  @Test
  @EnabledIf("reactorCheckoutPresent")
  void referenceAppPomMatchesAgentYml() throws Exception {
    Path root = reactorRoot();
    Path appPom = root.resolve("app/pom.xml");
    Path manifest = root.resolve("app/src/main/resources").resolve(CompositionPaths.DEFAULT_MANIFEST);

    ReactorCatalog catalog = new ReactorCatalog(discoverReactorModules(root));
    var resolution = new CapabilityResolver(catalog)
            .resolve(CompositionManifestParser.parse(manifest).buildTime());

    assertTrue(resolution.unknownExcept().isEmpty(),
            () -> "unknown except entries: " + resolution.unknownExcept());

    Model model;
    try (Reader reader = Files.newBufferedReader(appPom)) {
      model = new MavenXpp3Reader().read(reader);
    }

    PomDrift drift = PomComposer.plan(model, resolution);
    assertEquals(new PomDrift(List.of(), List.of()), drift,
            () -> "reference app is out of sync — run mvn qlawkus:generate -pl app -am; "
                    + "added=" + drift.added() + " removed=" + drift.removed());
  }

  static boolean reactorCheckoutPresent() {
    try {
      return Files.isRegularFile(reactorRoot().resolve("app/pom.xml"));
    } catch (IllegalStateException e) {
      return false;
    }
  }

  private static Path reactorRoot() {
    Path here = Path.of("").toAbsolutePath().normalize();
    Path parent = here.getParent();
    if (parent != null && Files.isRegularFile(parent.resolve("app/pom.xml"))) {
      return parent;
    }
    throw new IllegalStateException("not running from composition-maven-plugin inside the reactor");
  }

  private static List<ReactorModule> discoverReactorModules(Path root) throws Exception {
    List<ReactorModule> modules = new ArrayList<>();
    Path rootPom = root.resolve("pom.xml");
    try (Stream<Path> descriptors = Files.walk(root)) {
      for (Path descriptor : descriptors.filter(ReferenceAppCompositionTest::isExtensionDescriptor).toList()) {
        Path modulePom = nearestModulePom(descriptor, root, rootPom);
        if (modulePom == null) {
          continue;
        }
        Model model = readModel(modulePom);
        modules.add(new ReactorModule(effectiveGroupId(model), model.getArtifactId(), descriptor));
      }
    }
    return modules;
  }

  private static boolean isExtensionDescriptor(Path path) {
    String normalized = path.toString().replace('\\', '/');
    return path.getFileName().toString().equals("quarkus-extension.yaml")
            && normalized.contains("META-INF")
            && !normalized.contains("/src/it/");
  }

  private static Path nearestModulePom(Path descriptor, Path root, Path rootPom) {
    Path dir = descriptor.getParent();
    while (dir != null && !dir.equals(root)) {
      Path candidate = dir.resolve("pom.xml");
      if (Files.isRegularFile(candidate) && !candidate.equals(rootPom)) {
        return candidate;
      }
      dir = dir.getParent();
    }
    return null;
  }

  private static String effectiveGroupId(Model model) {
    if (model.getGroupId() != null && !model.getGroupId().isBlank()) {
      return model.getGroupId();
    }
    if (model.getParent() != null && model.getParent().getGroupId() != null) {
      return model.getParent().getGroupId();
    }
    throw new IllegalStateException("no groupId on module pom");
  }

  private static Model readModel(Path pom) throws Exception {
    try (Reader reader = Files.newBufferedReader(pom)) {
      return new MavenXpp3Reader().read(reader);
    } catch (XmlPullParserException e) {
      throw new Exception("cannot read " + pom, e);
    }
  }
}
