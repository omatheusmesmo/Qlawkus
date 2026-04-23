package dev.omatheusmesmo.qlawkus.cognition;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class JournalTest {

  @AfterEach
  @Transactional
  void cleanup() {
    Journal.deleteAll();
  }

  @Test
  @Transactional
  void persist_andFindById() {
    Journal journal = new Journal();
    journal.date = LocalDate.of(2026, 4, 22);
    journal.summary = "Discussed Java patterns and refactoring strategies.";
    journal.messageCount = 12;
    journal.persist();

    Journal found = Journal.findById(journal.id);

    assertNotNull(found);
    assertEquals(LocalDate.of(2026, 4, 22), found.date);
    assertEquals("Discussed Java patterns and refactoring strategies.", found.summary);
    assertEquals(12, found.messageCount);
  }

  @Test
  @Transactional
  void findByDate_returnsMatchingJournal() {
    Journal journal = new Journal();
    journal.date = LocalDate.of(2026, 4, 22);
    journal.summary = "Summary A";
    journal.messageCount = 5;
    journal.persist();

    Journal found = Journal.findByDate(LocalDate.of(2026, 4, 22));

    assertNotNull(found);
    assertEquals("Summary A", found.summary);
  }

  @Test
  @Transactional
  void findByDate_returnsNullForMissingDate() {
    Journal found = Journal.findByDate(LocalDate.of(2020, 1, 1));

    assertEquals(null, found);
  }

  @Test
  @Transactional
  void existsForDate_returnsTrueWhenPresent() {
    Journal journal = new Journal();
    journal.date = LocalDate.of(2026, 4, 22);
    journal.summary = "Summary";
    journal.messageCount = 3;
    journal.persist();

    assertTrue(Journal.existsForDate(LocalDate.of(2026, 4, 22)));
  }

  @Test
  @Transactional
  void existsForDate_returnsFalseWhenAbsent() {
    assertEquals(false, Journal.existsForDate(LocalDate.of(2020, 1, 1)));
  }

  @Test
  @Transactional
  void prePersist_setsTimestamps() {
    Journal journal = new Journal();
    journal.date = LocalDate.now();
    journal.summary = "Test";
    journal.messageCount = 1;
    journal.persist();

    assertNotNull(journal.createdAt);
    assertNotNull(journal.updatedAt);
  }
}
