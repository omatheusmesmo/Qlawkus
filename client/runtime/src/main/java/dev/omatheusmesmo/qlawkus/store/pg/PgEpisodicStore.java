package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class PgEpisodicStore implements EpisodicStore {

  @Override
  @Transactional
  public boolean existsForDate(LocalDate date) {
    return Journal.existsForDate(date);
  }

  @Override
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void storeEpisode(LocalDate date, String summary, int messageCount) {
    if (Journal.existsForDate(date)) return;
    Journal journal = new Journal();
    journal.date = date;
    journal.summary = summary;
    journal.messageCount = messageCount;
    journal.persist();
  }

  @Override
  @Transactional
  public List<JournalSummary> listJournals() {
    return Journal.listAll().stream()
      .map(j -> (Journal) j)
      .map(j -> new JournalSummary(j.id, j.date, j.summary, j.messageCount, j.createdAt))
      .toList();
  }

  @Override
  @Transactional
  public long count() {
    return Journal.count();
  }

  @Override
  @Transactional
  public long purgeAll() {
    return Journal.deleteAll();
  }
}
