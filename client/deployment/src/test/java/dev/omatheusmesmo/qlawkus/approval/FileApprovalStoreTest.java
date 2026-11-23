package dev.omatheusmesmo.qlawkus.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileApprovalStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final String ID = "0123456789abcdef0123456789abcdef";

    @TempDir
    Path root;

    @Test
    void aCreatedRequestIsFoundAgainByIdAndKeepsItsBinding() {
        FileApprovalStore store = new FileApprovalStore(root, 10);
        store.create(pending(ID));

        Optional<ApprovalRequest> found = store.find(ID);

        assertTrue(found.isPresent());
        assertEquals("sendEmail", found.get().toolName());
        assertEquals(ApprovalStatus.PENDING, found.get().status());
    }

    @Test
    void aRequestSurvivesARestartBecauseTheNewStoreReadsTheSameDirectory() {
        new FileApprovalStore(root, 10).create(pending(ID));

        FileApprovalStore afterRestart = new FileApprovalStore(root, 10);

        assertEquals(ApprovalStatus.PENDING, afterRestart.find(ID).orElseThrow().status());
        assertEquals(1, afterRestart.list().size());
    }

    @Test
    void aTransitionAppliesOnlyWhileTheRecordStillHasTheExpectedStatus() {
        FileApprovalStore store = new FileApprovalStore(root, 10);
        store.create(pending(ID));

        Optional<ApprovalRequest> first = store.transition(ID, ApprovalStatus.PENDING,
                request -> request.decide(ApprovalStatus.APPROVED, NOW, "owner"));
        Optional<ApprovalRequest> second = store.transition(ID, ApprovalStatus.PENDING,
                request -> request.decide(ApprovalStatus.DENIED, NOW, "owner"));

        assertTrue(first.isPresent());
        assertTrue(second.isEmpty());
        assertEquals(ApprovalStatus.APPROVED, new FileApprovalStore(root, 10).find(ID).orElseThrow().status());
    }

    @Test
    void aTransitionIsPersistedBeforeItReturns() {
        FileApprovalStore store = new FileApprovalStore(root, 10);
        store.create(pending(ID));

        store.transition(ID, ApprovalStatus.PENDING, request -> request.decide(ApprovalStatus.DENIED, NOW, "owner"));

        assertEquals(ApprovalStatus.DENIED, new FileApprovalStore(root, 10).find(ID).orElseThrow().status());
        assertEquals("owner", new FileApprovalStore(root, 10).find(ID).orElseThrow().decidedBy());
    }

    @Test
    void aCorruptFileIsSkippedAndDoesNotStopTheStoreFromLoading() throws IOException {
        new FileApprovalStore(root, 10).create(pending(ID));
        Files.writeString(root.resolve("ffffffffffffffffffffffffffffffff.json"), "{not json");

        FileApprovalStore reloaded = new FileApprovalStore(root, 10);

        assertEquals(1, reloaded.list().size());
        assertTrue(reloaded.find(ID).isPresent());
    }

    @Test
    void theOldestTerminalRequestsArePrunedAndPendingOnesAreKept() {
        FileApprovalStore store = new FileApprovalStore(root, 1);
        store.create(pending("00000000000000000000000000000001"));
        store.create(pending("00000000000000000000000000000002"));
        store.create(pending("00000000000000000000000000000003"));
        store.transition("00000000000000000000000000000001", ApprovalStatus.PENDING,
                request -> request.decide(ApprovalStatus.DENIED, NOW, "owner"));
        store.transition("00000000000000000000000000000002", ApprovalStatus.PENDING,
                request -> request.decide(ApprovalStatus.DENIED, NOW, "owner"));
        store.create(pending("00000000000000000000000000000004"));

        List<String> ids = store.list().stream().map(ApprovalRequest::id).toList();

        assertFalse(ids.contains("00000000000000000000000000000001"));
        assertTrue(ids.contains("00000000000000000000000000000002"));
        assertTrue(ids.contains("00000000000000000000000000000003"));
        assertTrue(ids.contains("00000000000000000000000000000004"));
    }

    @Test
    void verifyWritableProvesAWriteWithoutLeavingAFileBehind() throws IOException {
        FileApprovalStore store = new FileApprovalStore(root.resolve("approvals"), 10);

        store.verifyWritable();

        try (Stream<Path> entries = Files.list(root.resolve("approvals"))) {
            assertEquals(0, entries.count());
        }
    }

    @Test
    void verifyWritableFailsWhenTheRootCannotBeCreated() throws IOException {
        Path blocker = root.resolve("blocker");
        Files.writeString(blocker, "not a directory");
        FileApprovalStore store = new FileApprovalStore(blocker.resolve("approvals"), 10);

        assertThrows(RuntimeException.class, store::verifyWritable);
    }

    @Test
    void anUnknownOrUnsafeIdIsNeverLookedUp() {
        FileApprovalStore store = new FileApprovalStore(root, 10);

        assertTrue(store.find("../../etc/passwd").isEmpty());
        assertTrue(store.find("").isEmpty());
    }

    private static ApprovalRequest pending(String id) {
        return new ApprovalRequest(id, "sendEmail", "{\"to\":\"ana@example.com\"}", "hash", "sendEmail {...}",
                "telegram:42", "telegram", "42", ApprovalStatus.PENDING, NOW, NOW.plusSeconds(86_400), null,
                null, null);
    }
}
