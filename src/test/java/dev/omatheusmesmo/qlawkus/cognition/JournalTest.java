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

  @Test
  @Transactional
  void findByDateRange_returnsJournalsInRange() {
    Journal j1 = new Journal();
    j1.date = LocalDate.of(2026, 4, 20);
    j1.summary = "Day 20";
    j1.messageCount = 3;
    j1.persist();

    Journal j2 = new Journal();
    j2.date = LocalDate.of(2026, 4, 22);
    j2.summary = "Day 22";
    j2.messageCount = 5;
    j2.persist();

    Journal j3 = new Journal();
    j3.date = LocalDate.of(2026, 4, 25);
    j3.summary = "Day 25";
    j3.messageCount = 2;
    j3.persist();

    var results = Journal.findByDateRange(LocalDate.of(2026, 4, 19), LocalDate.of(2026, 4, 22));

    assertEquals(2, results.size());
    assertEquals(LocalDate.of(2026, 4, 20), results.get(0).date);
    assertEquals(LocalDate.of(2026, 4, 22), results.get(1).date);
  }
}
