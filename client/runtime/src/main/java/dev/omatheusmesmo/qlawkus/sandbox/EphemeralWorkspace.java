package dev.omatheusmesmo.qlawkus.sandbox;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Provisions disposable working directories under the system temp dir for one-shot
 * sandboxed operations (e.g. running a PR's tests, applying a patch, code-review
 * scratch space). Each handle is unique, owner-only (POSIX {@code 0700}), and is
 * recursively deleted by {@link Handle#close()}.
 *
 * <p>Typical usage:
 * <pre>{@code
 * try (EphemeralWorkspace.Handle ws = ephemeralWorkspace.create()) {
 *     // ws.path() points at /tmp/claw-<uuid>/
 * } // directory and contents removed here
 * }</pre>
 */
@ApplicationScoped
public class EphemeralWorkspace {

    static final String PREFIX = "claw-";

    /**
     * Allocates a fresh sandbox directory.
     *
     * @return a handle whose {@link Handle#close()} removes the directory and its contents
     * @throws SandboxException if the directory cannot be created
     */
    public Handle create() {
        Path root = Path.of(System.getProperty("java.io.tmpdir"));
        String name = PREFIX + UUID.randomUUID();
        try {
            Path dir = createOwnerOnly(root, name);
            Log.debugf("EphemeralWorkspace: created %s", dir);
            return new Handle(dir);
        } catch (IOException e) {
            throw new SandboxException("Failed to create ephemeral workspace under " + root, e);
        }
    }

    private static Path createOwnerOnly(Path root, String name) throws IOException {
        Path dir = root.resolve(name);
        if (supportsPosix(root)) {
            Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rwx------");
            FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(ownerOnly);
            return Files.createDirectory(dir, attr);
        }
        return Files.createDirectory(dir);
    }

    private static boolean supportsPosix(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    /**
     * Handle on a single ephemeral directory. The directory is removed when {@link #close()}
     * is called; cleanup is best-effort and never throws.
     */
    public static final class Handle implements AutoCloseable {

        private final Path path;
        private volatile boolean closed;

        Handle(Path path) {
            this.path = path;
        }

        public Path path() {
            return path;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                deleteRecursively(path);
                Log.debugf("EphemeralWorkspace: removed %s", path);
            } catch (IOException e) {
                Log.warnf(e, "EphemeralWorkspace: failed to fully remove %s", path);
            }
        }

        public boolean isClosed() {
            return closed;
        }

        private static void deleteRecursively(Path root) throws IOException {
            if (!Files.exists(root)) {
                return;
            }
            Files.walkFileTree(root, EnumSet.noneOf(java.nio.file.FileVisitOption.class), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.deleteIfExists(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.deleteIfExists(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
        }
    }
}
