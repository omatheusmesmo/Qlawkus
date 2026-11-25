package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;

/**
 * Postgres-backed {@link EpisodicStore}, active when {@code qlawkus.cognition.backend=pgvector}. Journals are rows in the {@code Journal} table; chosen at boot by {@link CognitionStoreSelector}.
 */
@ApplicationScoped
@Typed(PgEpisodicStore.class)
public class PgEpisodicStore implements EpisodicStore {

  @Inject
  PgWriteProbe writeProbe;

  @Inject
  JournalRepository journals;

  @Override
  public boolean existsForDate(LocalDate date) {
    return journals.existsForDate(date);
  }

  @Override
  public void storeEpisode(LocalDate date, String summary, int messageCount) {
    journals.store(date, summary, messageCount);
  }

  @Override
  public List<JournalSummary> listJournals() {
    return journals.listJournals();
  }

  @Override
  public long count() {
    return journals.count();
  }

  @Override
  public long purgeAll() {
    return journals.deleteAll();
  }

  @Override
  public void verifyWritable() {
    writeProbe.verify(PgWriteProbe.Table.JOURNAL);
  }
}
