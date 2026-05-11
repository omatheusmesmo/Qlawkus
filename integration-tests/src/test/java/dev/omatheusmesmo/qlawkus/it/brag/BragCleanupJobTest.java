package dev.omatheusmesmo.qlawkus.it.brag;

import dev.langchain4j.model.chat.ChatModel;
import dev.omatheusmesmo.qlawkus.tools.brag.BragCleanupJob;
import dev.omatheusmesmo.qlawkus.tools.brag.BragEntry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(BragTestProfile.class)
@Execution(ExecutionMode.SAME_THREAD)
class BragCleanupJobTest {

    @InjectMock
    ChatModel chatModel;

    @Inject
    BragCleanupJob cleanupJob;

    @AfterEach
    @Transactional
    void cleanup() {
        BragEntry.deleteAll();
    }

    @Test
    @Transactional
    void purgeExpiredEntries_removesOldSoftDeletedEntries() {
        BragEntry old = new BragEntry();
        old.date = java.time.LocalDate.of(2020, 1, 1);
        old.achievement = "Old achievement";
        old.deleted = true;
        old.createdAt = Instant.now().minus(30, ChronoUnit.DAYS);
        old.persist();

        BragEntry recent = new BragEntry();
        recent.date = java.time.LocalDate.of(2026, 5, 7);
        recent.achievement = "Recent achievement";
        recent.deleted = true;
        recent.createdAt = Instant.now().minus(1, ChronoUnit.DAYS);
        recent.persist();

        BragEntry active = new BragEntry();
        active.date = java.time.LocalDate.of(2026, 5, 7);
        active.achievement = "Active achievement";
        active.deleted = false;
        active.createdAt = Instant.now().minus(30, ChronoUnit.DAYS);
        active.persist();

        BragEntry.flush();

        cleanupJob.purgeExpiredEntries();

        BragEntry.flush();
        BragEntry.getEntityManager().clear();

        assertEquals(2, BragEntry.count());
        assertFalse(BragEntry.find("id = ?1", old.id).firstResult() != null, "Old soft-deleted entry should be purged");
        assertTrue(BragEntry.find("id = ?1", recent.id).firstResult() != null, "Recent soft-deleted entry should remain");
        assertTrue(BragEntry.find("id = ?1", active.id).firstResult() != null, "Active old entry should remain");
    }

    @Test
    @Transactional
    void purgeExpiredEntries_marksDuplicatesAsDeleted() {
        BragEntry first = new BragEntry();
        first.date = java.time.LocalDate.of(2026, 5, 7);
        first.achievement = "Same achievement";
        first.repo = null;
        first.deleted = false;
        first.createdAt = Instant.now().minus(2, ChronoUnit.DAYS);
        first.persist();

        BragEntry second = new BragEntry();
        second.date = java.time.LocalDate.of(2026, 5, 7);
        second.achievement = "Same achievement";
        second.repo = null;
        second.deleted = false;
        second.createdAt = Instant.now().minus(1, ChronoUnit.DAYS);
        second.persist();

        BragEntry.flush();

        cleanupJob.purgeExpiredEntries();

        BragEntry flushed = BragEntry.find("id = ?1", second.id).firstResult();
        assertTrue(flushed.deleted, "Later duplicate should be marked as deleted");

        BragEntry firstReloaded = BragEntry.find("id = ?1", first.id).firstResult();
        assertFalse(firstReloaded.deleted, "Earlier entry should remain active");
    }

    @Test
    @Transactional
    void purgeExpiredEntries_noopWhenNoExpiredOrDuplicates() {
        BragEntry entry = new BragEntry();
        entry.date = java.time.LocalDate.of(2026, 5, 7);
        entry.achievement = "Unique achievement";
        entry.deleted = false;
        entry.createdAt = Instant.now();
        entry.persist();

        cleanupJob.purgeExpiredEntries();

        assertEquals(1, BragEntry.count());
        BragEntry reloaded = BragEntry.find("id = ?1", entry.id).firstResult();
        assertFalse(reloaded.deleted);
    }
}
