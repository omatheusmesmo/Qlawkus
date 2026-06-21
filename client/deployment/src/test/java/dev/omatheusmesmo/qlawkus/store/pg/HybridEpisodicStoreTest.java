package dev.omatheusmesmo.qlawkus.store.pg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit test for the hybrid episodic backend: every write must land in the dated {@code .md} files
 * (the source of truth) AND be mirrored into the {@code Journal} table, while reads go through the
 * table. Uses a fake {@link JournalRepository} (in-memory, no database), so the file/mirror logic is
 * exercised without Postgres.
 */
class HybridEpisodicStoreTest {

  @TempDir
  Path tempDir;

  private HybridEpisodicStore store(FakeJournals journals) {
    return new HybridEpisodicStore(tempDir.toString(), journals);
  }

  private long markdownFileCount() throws IOException {
    try (Stream<Path> files = Files.list(tempDir)) {
      return files.filter(p -> p.getFileName().toString().endsWith(".md")).count();
    }
  }

  @Test
  void storeWritesFileAndMirrorsToTable() throws IOException {
    FakeJournals journals = new FakeJournals();
    HybridEpisodicStore store = store(journals);
    LocalDate date = LocalDate.of(2026, 6, 19);

    store.storeEpisode(date, "Discussed episodic backends.", 8);

    assertEquals(1, markdownFileCount());
    assertTrue(Files.isRegularFile(tempDir.resolve("2026-06-19.md")));
    assertEquals(1, journals.count());
    assertEquals(date, journals.listJournals().get(0).date());
  }

  @Test
  void storeSkipsWhenDayAlreadyMirrored() throws IOException {
    FakeJournals journals = new FakeJournals();
    journals.store(LocalDate.of(2026, 6, 19), "already there", 1);
    HybridEpisodicStore store = store(journals);

    store.storeEpisode(LocalDate.of(2026, 6, 19), "should be skipped", 2);

    assertEquals(0, markdownFileCount());
    assertEquals(1, journals.count());
  }

  @Test
  void readsGoThroughTableAndCarryDatabaseIds() {
    FakeJournals journals = new FakeJournals();
    HybridEpisodicStore store = store(journals);

    store.storeEpisode(LocalDate.of(2026, 6, 19), "monday", 3);

    JournalSummary summary = store.listJournals().get(0);
    assertEquals(1L, summary.id());
    assertEquals(1, store.count());
    assertTrue(store.existsForDate(LocalDate.of(2026, 6, 19)));
    assertFalse(store.existsForDate(LocalDate.of(2026, 6, 20)));
  }

  @Test
  void purgeAllDeletesFilesAndDelegatesToTable() throws IOException {
    FakeJournals journals = new FakeJournals();
    HybridEpisodicStore store = store(journals);
    store.storeEpisode(LocalDate.of(2026, 6, 18), "one", 1);
    store.storeEpisode(LocalDate.of(2026, 6, 19), "two", 1);

    long purged = store.purgeAll();

    assertEquals(2, purged);
    assertEquals(0, markdownFileCount());
    assertEquals(0, journals.count());
  }

  /** Fake repository: in-memory journals keyed by date, no EntityManager or Panache. */
  private static final class FakeJournals extends JournalRepository {

    private final List<JournalSummary> rows = new ArrayList<>();
    private long sequence = 0;

    @Override
    public boolean existsForDate(LocalDate date) {
      return rows.stream().anyMatch(j -> j.date().equals(date));
    }

    @Override
    public void store(LocalDate date, String summary, int messageCount) {
      if (existsForDate(date)) {
        return;
      }
      rows.add(new JournalSummary(++sequence, date, summary, messageCount, Instant.now()));
    }

    @Override
    public List<JournalSummary> listJournals() {
      return List.copyOf(rows);
    }

    @Override
    public long count() {
      return rows.size();
    }

    @Override
    public long deleteAll() {
      long n = rows.size();
      rows.clear();
      return n;
    }
  }
}
