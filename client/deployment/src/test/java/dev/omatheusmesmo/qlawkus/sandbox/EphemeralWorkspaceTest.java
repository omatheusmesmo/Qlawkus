package dev.omatheusmesmo.qlawkus.sandbox;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class EphemeralWorkspaceTest {

    @Inject
    EphemeralWorkspace ephemeralWorkspace;

    @Test
    void create_returnsExistingDirectoryUnderTmpDir() {
        Path tmpRoot = Path.of(System.getProperty("java.io.tmpdir"));

        try (EphemeralWorkspace.Handle handle = ephemeralWorkspace.create()) {
            Path path = handle.path();

            assertTrue(Files.isDirectory(path), "Expected directory to exist: " + path);
            assertTrue(path.startsWith(tmpRoot), "Expected " + path + " to be under " + tmpRoot);
            assertTrue(path.getFileName().toString().startsWith(EphemeralWorkspace.PREFIX),
                    "Expected name to start with prefix, was " + path.getFileName());
        }
    }

    @Test
    void create_returnsUniquePathsAcrossCalls() {
        try (EphemeralWorkspace.Handle a = ephemeralWorkspace.create();
                EphemeralWorkspace.Handle b = ephemeralWorkspace.create()) {
            assertNotEquals(a.path(), b.path());
            assertTrue(Files.exists(a.path()));
            assertTrue(Files.exists(b.path()));
        }
    }

    @Test
    void close_removesDirectoryAndAllContents() throws Exception {
        Path path;
        try (EphemeralWorkspace.Handle handle = ephemeralWorkspace.create()) {
            path = handle.path();
            Files.writeString(path.resolve("file.txt"), "hello");
            Path nested = Files.createDirectory(path.resolve("nested"));
            Files.writeString(nested.resolve("deep.txt"), "world");
            assertTrue(Files.exists(path.resolve("file.txt")));
        }

        assertFalse(Files.exists(path), "Ephemeral directory should be removed after close: " + path);
    }

    @Test
    void close_isIdempotentAndDoesNotThrowOnSecondInvocation() {
        EphemeralWorkspace.Handle handle = ephemeralWorkspace.create();
        handle.close();
        assertTrue(handle.isClosed());
        assertDoesNotThrow(handle::close);
    }

    @Test
    @EnabledOnOs({ OS.LINUX, OS.MAC })
    void create_setsOwnerOnlyPermissionsOnPosixFilesystems() throws Exception {
        try (EphemeralWorkspace.Handle handle = ephemeralWorkspace.create()) {
            PosixFileAttributes attrs = Files.readAttributes(handle.path(), PosixFileAttributes.class);
            Set<PosixFilePermission> permissions = attrs.permissions();

            assertEquals(
                    Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE),
                    permissions,
                    "Ephemeral workspace must not be readable or writable by group/other");
        }
    }
}
