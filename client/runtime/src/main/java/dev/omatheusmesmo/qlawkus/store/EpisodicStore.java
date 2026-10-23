package dev.omatheusmesmo.qlawkus.store;

import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import java.time.LocalDate;
import java.util.List;

public interface EpisodicStore {

  boolean existsForDate(LocalDate date);

  void storeEpisode(LocalDate date, String summary, int messageCount);

  List<JournalSummary> listJournals();

  long count();

  long purgeAll();

  /**
   * Proves this store could persist a change right now, without changing anything, because readiness
   * calls it on every poll. Throws when a write would fail: a read-only volume, a root owned by
   * another uid, a read-only database session, or a missing table privilege.
   */
  void verifyWritable();
}
