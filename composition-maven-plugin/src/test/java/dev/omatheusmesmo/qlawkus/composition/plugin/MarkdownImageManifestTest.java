package dev.omatheusmesmo.qlawkus.composition.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.omatheusmesmo.qlawkus.composition.BuildTime;
import dev.omatheusmesmo.qlawkus.composition.CompositionManifestParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Pins the manifest of the markdown image variant to the reference one: the same composition minus
 * the capabilities that need a datasource, so the two never drift apart.
 */
class MarkdownImageManifestTest {

  private static final List<String> NEEDS_A_DATASOURCE = List.of("cognition.pgvector", "google-workspace", "brag");

  @Test
  @EnabledIf("reactorCheckoutPresent")
  void markdownManifestIsTheReferenceOneWithoutTheDatasourceCapabilities() {
    BuildTime reference = CompositionManifestParser.parse(referenceManifest()).buildTime();
    BuildTime markdown = CompositionManifestParser.parse(markdownManifest()).buildTime();

    List<String> expected = new ArrayList<>(reference.except());
    expected.removeAll(NEEDS_A_DATASOURCE);

    assertEquals(reference.defaultPosture(), markdown.defaultPosture());
    assertEquals(expected, markdown.except());
    for (String capability : NEEDS_A_DATASOURCE) {
      assertFalse(markdown.isEnabled(capability), capability + " needs a datasource");
    }
  }

  static boolean reactorCheckoutPresent() {
    return Files.isRegularFile(referenceManifest());
  }

  private static Path markdownManifest() {
    return reactorRoot().resolve("app/src/main/docker/agent-markdown.yml");
  }

  private static Path referenceManifest() {
    return reactorRoot().resolve("app/src/main/resources/qlawkus/agent.yml");
  }

  private static Path reactorRoot() {
    return Path.of("").toAbsolutePath().normalize().getParent();
  }
}
