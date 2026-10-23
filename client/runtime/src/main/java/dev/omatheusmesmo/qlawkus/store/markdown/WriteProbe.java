package dev.omatheusmesmo.qlawkus.store.markdown;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The write check every file-backed store answers {@code verifyWritable} with: write one byte to a
 * file in the root and delete it again. An actual write is closer to what the next store write will
 * meet than {@link Files#isWritable}, which is an access check: that misses a volume out of inodes
 * and, on network filesystems, can disagree with what the server will accept. The byte is not
 * synced, so a volume that only fails at flush time still passes.
 *
 * <p>The probe has one fixed, dot-prefixed name that no store's listing treats as content, and it is
 * deleted in the same call, so polling readiness never shows up in the stores. The name is fixed
 * rather than random so that on a root where deletes fail, each poll reuses the one file instead of
 * leaving another. A missing root is created, as the store's own first write would, so an empty
 * volume reads as writable rather than broken.
 */
public final class WriteProbe {

  private static final String PROBE = ".qlawkus-write-probe";
  private static final byte[] ONE_BYTE = {0};

  private WriteProbe() {
  }

  public static void verify(Path root) {
    try {
      Files.createDirectories(root);
      Path probe = root.resolve(PROBE);
      Files.write(probe, ONE_BYTE);
      Files.deleteIfExists(probe);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write to " + root, e);
    }
  }
}
