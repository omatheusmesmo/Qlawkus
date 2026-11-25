package dev.omatheusmesmo.qlawkus.composition.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.composition.BuildTime;
import dev.omatheusmesmo.qlawkus.composition.CompositionManifestParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Pins the manifest of the published image to the reference one: the same composition, so the image
 * carries every capability and the two never drift apart.
 */
class ImageManifestTest {

  @Test
  @EnabledIf("reactorCheckoutPresent")
  void imageManifestIsTheReferenceOne() {
    BuildTime reference = CompositionManifestParser.parse(referenceManifest()).buildTime();
    BuildTime image = CompositionManifestParser.parse(imageManifest()).buildTime();

    assertEquals(reference.defaultPosture(), image.defaultPosture());
    assertEquals(reference.except(), image.except());
    assertTrue(image.isEnabled("cognition.pgvector"), "the image switches to Postgres at boot, so it carries the backend");
    for (String capability : List.of("google-workspace", "brag")) {
      assertTrue(image.isEnabled(capability), capability + " rides in the image, its database off until a URL is given");
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
