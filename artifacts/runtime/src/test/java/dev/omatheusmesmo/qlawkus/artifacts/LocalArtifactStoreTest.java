package dev.omatheusmesmo.qlawkus.artifacts;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.artifacts.LocalArtifactStore.Destination;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalArtifactStoreTest {

    @TempDir
    Path directory;

    @Test
    void persistsAnOwnerAndDestinationBoundCopyThenExpiresIt() throws Exception {
        Instant start = Instant.parse("2026-01-01T10:30:00Z");
        Clock clock = Clock.fixed(start, ZoneOffset.UTC);
        var destination = new Destination("discord", "channel");
        var file = new WorkspaceArtifact.File("report.csv", "text/csv", new byte[] {1, 2, 3});
        var handle = new LocalArtifactStore(directory, clock).save("owner", destination, file);

        var reopened = new LocalArtifactStore(directory, clock);
        assertEquals(start.plus(LocalArtifactStore.TTL), handle.expiresAt());
        assertEquals(handle, reopened.list("owner").getFirst());
        assertTrue(reopened.list("another-user").isEmpty());
        assertTrue(reopened.read(handle.id(), "another-user").isEmpty());
        assertTrue(reopened.read(handle.id(), "owner", new Destination("discord", "another-channel")).isEmpty());
        assertTrue(reopened.read(handle.id(), "owner", new Destination("telegram", "channel")).isEmpty());
        assertArrayEquals(file.bytes(), reopened.read(handle.id(), "owner", destination).orElseThrow().bytes());

        var expired = new LocalArtifactStore(directory, Clock.fixed(handle.expiresAt(), ZoneOffset.UTC));
        assertTrue(expired.read(handle.id(), "owner").isEmpty());
        assertFalse(Files.exists(directory.resolve(handle.id() + ".artifact")));
        assertTrue(expired.list("owner").isEmpty());
    }

    @Test
    void cleanupRemovesExpiredFilesAndInterruptedWrites() throws Exception {
        Instant start = Instant.parse("2026-01-01T10:30:00Z");
        var store = new LocalArtifactStore(directory, Clock.fixed(start, ZoneOffset.UTC));
        var handle = store.save("owner", null, new WorkspaceArtifact.File("report.txt", "text/plain", new byte[0]));
        Path interruptedWrite = Files.createFile(directory.resolve("artifact-interrupted.part"));

        new LocalArtifactStore(directory, Clock.fixed(handle.expiresAt(), ZoneOffset.UTC)).expire();

        assertFalse(Files.exists(interruptedWrite));
        assertFalse(Files.exists(directory.resolve(handle.id() + ".artifact")));
    }

    @Test
    void refusesToExceedCountOrByteQuotas() throws Exception {
        var countStore = new LocalArtifactStore(directory.resolve("count"), Clock.systemUTC());
        var small = new WorkspaceArtifact.File("report.txt", "text/plain", new byte[10]);
        for (int i = 0; i < LocalArtifactStore.MAX_COUNT; i++) {
            countStore.save("owner", null, small);
        }
        assertThrows(IllegalArgumentException.class, () -> countStore.save("owner", null, small));

        var byteStore = new LocalArtifactStore(directory.resolve("bytes"), Clock.systemUTC());
        var large = new WorkspaceArtifact.File("report.txt", "text/plain", new byte[WorkspaceArtifact.MAX_BYTES]);
        for (long bytes = 0; bytes < LocalArtifactStore.MAX_TOTAL_BYTES; bytes += large.bytes().length) {
            byteStore.save("owner", null, large);
        }
        assertThrows(IllegalArgumentException.class, () -> byteStore.save("owner", null, small));
    }
}
