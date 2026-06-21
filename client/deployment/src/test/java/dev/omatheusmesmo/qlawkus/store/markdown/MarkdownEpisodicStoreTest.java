package dev.omatheusmesmo.qlawkus.store.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit test for the markdown episodic backend: journals are dated {@code <date>.md} files, one per
 * day, with no database. Verifies write/read/dedup-by-date/count/purge and that a re-consolidation of
 * the same day is a no-op.
 */
class MarkdownEpisodicStoreTest {

  @TempDir
  Path tempDir;

  private MarkdownEpisodicStore store() {
    return new MarkdownEpisodicStore(tempDir.toString());
  }

  private long markdownFileCount() throws IOException {
    try (Stream<Path> files = Files.list(tempDir)) {
      return files.filter(p -> p.getFileName().toString().endsWith(".md")).count();
    }
  }

  @Test
  void storesJournalAsDatedFileAndReadsItBack() throws IOException {
    MarkdownEpisodicStore store = store();
    LocalDate date = LocalDate.of(2026, 6, 19);

    store.storeEpisode(date, "Discussed pluggable cognition backends.", 12);

    assertEquals(1, markdownFileCount());
    assertTrue(Files.isRegularFile(tempDir.resolve("2026-06-19.md")));
    List<JournalSummary> journals = store.listJournals();
    assertEquals(1, journals.size());
    JournalSummary journal = journals.get(0);
    assertEquals(date, journal.date());
    assertEquals("Discussed pluggable cognition backends.", journal.summary());
    assertEquals(12, journal.messageCount());
  }

  @Test
  void existsForDateReflectsStoredJournals() {
    MarkdownEpisodicStore store = store();
    LocalDate date = LocalDate.of(2026, 6, 19);

    assertFalse(store.existsForDate(date));
    store.storeEpisode(date, "summary", 1);
    assertTrue(store.existsForDate(date));
  }

  @Test
  void secondConsolidationOfSameDayIsNoOp() throws IOException {
    MarkdownEpisodicStore store = store();
    LocalDate date = LocalDate.of(2026, 6, 19);

    store.storeEpisode(date, "first", 1);
    store.storeEpisode(date, "second wins nothing", 99);

    assertEquals(1, markdownFileCount());
    assertEquals("first", store.listJournals().get(0).summary());
  }

  @Test
  void listJournalsReturnsAllDaysSortedByDate() {
    MarkdownEpisodicStore store = store();
    store.storeEpisode(LocalDate.of(2026, 6, 20), "tuesday", 2);
    store.storeEpisode(LocalDate.of(2026, 6, 18), "sunday", 4);
    store.storeEpisode(LocalDate.of(2026, 6, 19), "monday", 3);

    List<LocalDate> dates = store.listJournals().stream().map(JournalSummary::date).toList();

    assertEquals(List.of(
        LocalDate.of(2026, 6, 18), LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 20)), dates);
    assertEquals(3, store.count());
  }

  @Test
  void purgeAllRemovesEveryJournalFile() throws IOException {
    MarkdownEpisodicStore store = store();
    store.storeEpisode(LocalDate.of(2026, 6, 18), "one", 1);
    store.storeEpisode(LocalDate.of(2026, 6, 19), "two", 1);

    long purged = store.purgeAll();

    assertEquals(2, purged);
    assertEquals(0, markdownFileCount());
    assertEquals(0, store.count());
  }
}
