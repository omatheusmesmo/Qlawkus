package dev.omatheusmesmo.qlawkus.composition.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
class ImageManifestTest {

  private static final List<String> OWNS_A_DATASOURCE = List.of("google-workspace", "brag");

  @Test
  @EnabledIf("reactorCheckoutPresent")
  void imageManifestIsTheReferenceOneWithoutTheCapabilitiesThatOwnADatabase() {
    BuildTime reference = CompositionManifestParser.parse(referenceManifest()).buildTime();
    BuildTime image = CompositionManifestParser.parse(imageManifest()).buildTime();

    List<String> expected = new ArrayList<>(reference.except());
    expected.removeAll(OWNS_A_DATASOURCE);

    assertEquals(reference.defaultPosture(), image.defaultPosture());
    assertEquals(expected, image.except());
    assertTrue(image.isEnabled("cognition.pgvector"), "the image switches to Postgres at boot, so it carries the backend");
    for (String capability : OWNS_A_DATASOURCE) {
      assertFalse(image.isEnabled(capability), capability + " owns a named datasource");
    }
  }

  static boolean reactorCheckoutPresent() {
    return Files.isRegularFile(referenceManifest());
  }

  private static Path imageManifest() {
    return reactorRoot().resolve("app/src/main/docker/agent-image.yml");
  }

  private static Path referenceManifest() {
    return reactorRoot().resolve("app/src/main/resources/qlawkus/agent.yml");
  }

  private static Path reactorRoot() {
    return Path.of("").toAbsolutePath().normalize().getParent();
  }
}
