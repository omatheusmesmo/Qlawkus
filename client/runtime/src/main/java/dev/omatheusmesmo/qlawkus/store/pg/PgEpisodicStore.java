package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;

/**
 * Postgres-backed {@link EpisodicStore}, active when {@code qlawkus.cognition.backend=pgvector} (the
 * default). Journals are rows in the {@code Journal} table; selected at build time via
 * {@link IfBuildProperty}, so other backends do not wire it at all.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.cognition.backend", stringValue = "pgvector", enableIfMissing = true)
public class PgEpisodicStore implements EpisodicStore {

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
}
