package dev.omatheusmesmo.qlawkus.store.markdown;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.omatheusmesmo.qlawkus.skill.MarkdownSkillFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the write probe readiness relies on: it must fail on a root the process cannot write, which
 * {@code load()} alone never noticed, and it must leave nothing behind on a root it can, since
 * readiness runs it every few seconds against the live stores.
 *
 * <p>Each store gets a root of its own and only one is made read-only at a time, so a store that
 * probed some other root, rather than the one it writes to, would pass and be caught.
 */
class MarkdownWritabilityTest {

  private static final String STORES = "soul owner facts episodic workingMemory skills";

  @TempDir
  Path tempDir;

  @Test
  void probeLeavesNothingBehind() throws IOException {
    probes().forEach((store, probe) -> assertDoesNotThrow(probe::run, store));

    for (String store : STORES.split(" ")) {
      assertEquals(List.of(), list(tempDir.resolve(store)),
          store + ": a probe must not pollute the store it checks");
    }
  }

  @Test
  void aProbeLeftByAFailedDeleteIsReusedRatherThanAccumulated() throws IOException {
    Path root = Files.createDirectories(tempDir.resolve("facts"));
    Files.writeString(root.resolve(".qlawkus-write-probe"), "left by an earlier poll");

    WriteProbe.verify(root);

    assertEquals(List.of(), list(root),
        "on a root where deletes fail, every poll would otherwise leave one more file");
  }

  @Test
  void probeCreatesAMissingRootLikeTheFirstWriteWould() {
    Path fresh = tempDir.resolve("fresh");

    WriteProbe.verify(fresh);

    assertTrue(Files.isDirectory(fresh), "an empty volume is writable, not broken");
  }

  @Test
  void everyStoreRefusesTheRootItWritesToWhenThatRootIsReadOnly() throws IOException {
    Map<String, Runnable> probes = probes();
    for (String store : STORES.split(" ")) {
      Path root = Files.createDirectories(tempDir.resolve(store));
      Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("r-x------"));
      try {
        assumeFalse(Files.isWritable(root), "running as root, which ignores directory permissions");
        UncheckedIOException refused = assertThrows(UncheckedIOException.class,
            probes.get(store)::run, store + " reported a read-only root as writable");
        assertTrue(refused.getMessage().contains(root.toString()),
            "the failure must name the root an operator has to fix: " + refused.getMessage());
      } finally {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
      }
    }
  }

  private Map<String, Runnable> probes() {
    Map<String, Runnable> probes = new LinkedHashMap<>();
    probes.put("soul", new MarkdownSoulStore(root("soul"))::verifyWritable);
    probes.put("owner", new MarkdownUserProfileStore(root("owner"))::verifyWritable);
    probes.put("facts", new MarkdownFactStore(root("facts"), null)::verifyWritable);
    probes.put("episodic", new MarkdownEpisodicStore(root("episodic"))::verifyWritable);
    probes.put("workingMemory",
        new MarkdownWorkingMemoryStore(root("workingMemory"), null)::verifyWritable);
    probes.put("skills", new MarkdownSkillFiles(
        List.of(tempDir.resolve("skills"), tempDir.resolve("shared-skills")))::verifyWritable);
    return probes;
  }

  private String root(String store) {
    return tempDir.resolve(store).toString();
  }

  private static List<Path> list(Path root) throws IOException {
    try (Stream<Path> left = Files.list(root)) {
      return left.toList();
    }
  }
}
