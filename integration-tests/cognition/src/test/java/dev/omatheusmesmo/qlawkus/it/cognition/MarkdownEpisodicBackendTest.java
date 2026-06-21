package dev.omatheusmesmo.qlawkus.it.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownEpisodicStore;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test for the markdown episodic backend inside the assembled application. The store is
 * instantiated directly with a temp root (mirroring {@code MarkdownFactBackendTest}), so no separate
 * markdown-pinned module is needed: a journal is written as a dated {@code .md} file and read back,
 * proving the backend works end-to-end with no database. Full no-database boot is a later step (SP4).
 */
@QuarkusTest
class MarkdownEpisodicBackendTest {

  @TempDir
  Path journalsRoot;

  @Test
  void storesJournalAsDatedFileAndReadsItBack() throws IOException {
    MarkdownEpisodicStore store = new MarkdownEpisodicStore(journalsRoot.toString());
    LocalDate date = LocalDate.of(2026, 6, 19);

    store.storeEpisode(date, "User shipped the pluggable episodic backend.", 14);

    assertTrue(Files.isRegularFile(journalsRoot.resolve("2026-06-19.md")),
        "journal should be persisted as a dated .md file");
    List<JournalSummary> journals = store.listJournals();
    assertEquals(1, journals.size());
    assertEquals(date, journals.get(0).date());
    assertTrue(journals.get(0).summary().contains("pluggable episodic backend"));
  }
}
