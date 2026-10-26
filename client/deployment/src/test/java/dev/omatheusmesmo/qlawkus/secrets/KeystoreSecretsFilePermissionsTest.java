package dev.omatheusmesmo.qlawkus.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeystoreSecretsFilePermissionsTest {

    private static final char[] PASSWORD = "store-password".toCharArray();
    private static final String ALIAS = "qlawkus.test.secret";
    private static final String VALUE = "owner-only-value";
    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");

    @TempDir
    Path dir;

    @BeforeEach
    void requirePosix() {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "POSIX file permissions not supported on this platform");
    }

    @Test
    void freshKeystoreIsOwnerOnly() throws IOException {
        Path file = dir.resolve("secrets.p12");

        KeystoreSecrets.put(file, PASSWORD, ALIAS, VALUE);

        assertEquals(OWNER_ONLY, Files.getPosixFilePermissions(file));
    }

    @Test
    void overwritingWorldReadableKeystoreMakesItOwnerOnlyAndKeepsTheSecret() throws IOException {
        Path file = dir.resolve("secrets.p12");
        KeystoreSecrets.put(file, PASSWORD, "qlawkus.test.other", "other-value");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));

        KeystoreSecrets.put(file, PASSWORD, ALIAS, VALUE);

        assertEquals(OWNER_ONLY, Files.getPosixFilePermissions(file));
        assertEquals(VALUE, KeystoreSecrets.readAll(file.toUri().toURL(), PASSWORD).get(ALIAS));
    }

    @Test
    void noTemporaryFileIsLeftBehind() throws IOException {
        Path file = dir.resolve("secrets.p12");

        KeystoreSecrets.put(file, PASSWORD, ALIAS, VALUE);
        KeystoreSecrets.put(file, PASSWORD, "qlawkus.test.second", "second-value");

        try (Stream<Path> entries = Files.list(dir)) {
            assertEquals(Set.of(file), entries.collect(Collectors.toSet()));
        }
    }

    /**
     * A {@code secrets.p12} bind-mounted into a container on its own is writable while the directory
     * around it is not, so no temporary file can be created beside it. The write must still land.
     */
    @Test
    void keystoreInDirectoryThatRefusesNewFilesIsRewrittenInPlace() throws IOException {
        Path locked = Files.createDirectory(dir.resolve("mounted"));
        Path file = locked.resolve("secrets.p12");
        KeystoreSecrets.put(file, PASSWORD, "qlawkus.test.other", "other-value");
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-x------"));
        try {
            assumeTrue(!Files.isWritable(locked), "running as a user that ignores directory permissions");

            KeystoreSecrets.put(file, PASSWORD, ALIAS, VALUE);

            Map<String, String> secrets = KeystoreSecrets.readAll(file.toUri().toURL(), PASSWORD);
            assertEquals(VALUE, secrets.get(ALIAS));
            assertEquals("other-value", secrets.get("qlawkus.test.other"));
            assertEquals(OWNER_ONLY, Files.getPosixFilePermissions(file));
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }
}
