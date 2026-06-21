package dev.omatheusmesmo.qlawkus.cognition;

import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import dev.omatheusmesmo.qlawkus.dto.MemorySummary;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.MemorySource;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Backend-agnostic admin facade over the cognition stores. It injects only the SPIs
 * ({@link FactStore}, {@link EpisodicStore}, {@link WorkingMemoryStore}), so it carries no
 * transaction or JTA coupling: each store owns its own transaction boundary (the Postgres backends
 * are {@code @Transactional} internally; the markdown backends need none). This keeps the client
 * runtime free of a transaction manager for the database-free distribution.
 */
@ApplicationScoped
public class MemoryAdminService {

  @Inject
  FactStore factStore;

  @Inject
  EpisodicStore episodicStore;

  @Inject
  WorkingMemoryStore workingMemoryStore;

  public MemorySummary getMemorySummary() {
    List<String> sources = factStore.listSources();
    long journalCount = episodicStore.count();
    long chatMessageCount = workingMemoryStore.count();
    return new MemorySummary(sources, journalCount, chatMessageCount);
  }

  public List<JournalSummary> listJournals() {
    return episodicStore.listJournals();
  }

  public long purgeEmbeddingsBySource(String source) {
    long deleted = factStore.purgeBySource(source);
    Log.infof("Purged %d embeddings with source=%s", deleted, source);
    return deleted;
  }

  public long purgeJournals() {
    factStore.purgeBySource(MemorySource.EPISODIC_CONSOLIDATOR.value());
    long deleted = episodicStore.purgeAll();
    Log.infof("Purged %d journals", deleted);
    return deleted;
  }

  public void purgeAllMemory() {
    long embeddings = factStore.purgeAll();
    episodicStore.purgeAll();
    workingMemoryStore.purgeAll();
    Log.infof("Purged all memory: %d embeddings, journals, chat messages", embeddings);
  }
}
