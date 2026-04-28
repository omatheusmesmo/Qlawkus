package dev.omatheusmesmo.qlawkus.cognition;

import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import dev.omatheusmesmo.qlawkus.dto.MemorySummary;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class MemoryAdminService {

  @Inject
  FactStore factStore;

  @Inject
  EpisodicStore episodicStore;

  @Inject
  WorkingMemoryStore workingMemoryStore;

  @Transactional
  public MemorySummary getMemorySummary() {
    List<String> sources = factStore.listSources();
    long journalCount = episodicStore.count();
    long chatMessageCount = workingMemoryStore.count();
    return new MemorySummary(sources, journalCount, chatMessageCount);
  }

  @Transactional
  public List<JournalSummary> listJournals() {
    return episodicStore.listJournals();
  }

  @Transactional
  public long purgeEmbeddingsBySource(String source) {
    long deleted = factStore.purgeBySource(source);
    Log.infof("Purged %d embeddings with source=%s", deleted, source);
    return deleted;
  }

  @Transactional
  public long purgeJournals() {
    factStore.purgeBySource("episodic-consolidator");
    long deleted = episodicStore.purgeAll();
    Log.infof("Purged %d journals", deleted);
    return deleted;
  }

  @Transactional
  public void purgeAllMemory() {
    factStore.purgeBySource("semantic-extractor");
    episodicStore.purgeAll();
    workingMemoryStore.purgeAll();
    Log.info("Purged all memory: embeddings, journals, chat messages");
  }
}
