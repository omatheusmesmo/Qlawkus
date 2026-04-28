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
}
