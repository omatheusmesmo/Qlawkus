package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;

/**
 * Panache access to the {@code Journal} entity, wrapping its static calls behind instance methods so
 * the episodic stores (pg + hybrid) share one transactional seam and tests can fake it without a
 * database. Mirrors {@link EmbeddingRepository}.
 */
@ApplicationScoped
public class JournalRepository {

  @Transactional
  public boolean existsForDate(LocalDate date) {
    return Journal.existsForDate(date);
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void store(LocalDate date, String summary, int messageCount) {
    if (Journal.existsForDate(date)) {
      return;
    }
    Journal journal = new Journal();
    journal.date = date;
    journal.summary = summary;
    journal.messageCount = messageCount;
    journal.persist();
  }

  @Transactional
  public List<JournalSummary> listJournals() {
    return Journal.<Journal>listAll().stream()
        .map(j -> new JournalSummary(j.id, j.date, j.summary, j.messageCount, j.createdAt))
        .toList();
  }

  @Transactional
  public long count() {
    return Journal.count();
  }

  @Transactional
  public long deleteAll() {
    return Journal.deleteAll();
  }
}
