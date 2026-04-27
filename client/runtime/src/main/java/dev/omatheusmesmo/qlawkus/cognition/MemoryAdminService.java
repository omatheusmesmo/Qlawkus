package dev.omatheusmesmo.qlawkus.cognition;

import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import dev.omatheusmesmo.qlawkus.dto.MemorySummary;
import dev.omatheusmesmo.qlawkus.repository.EmbeddingRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class MemoryAdminService {

  @Inject
  EmbeddingRepository embeddingRepository;

  @Transactional
  public MemorySummary getMemorySummary() {
    List<String> sources = embeddingRepository.listSources();
    long journalCount = Journal.count();
    long chatMessageCount = ChatMessageEntity.count();
    return new MemorySummary(sources, journalCount, chatMessageCount);
  }

  @Transactional
  public List<JournalSummary> listJournals() {
    return Journal.listAll().stream()
        .map(j -> (Journal) j)
        .map(j -> new JournalSummary(j.id, j.date, j.summary, j.messageCount, j.createdAt))
        .toList();
  }

  @Transactional
  public long purgeEmbeddingsBySource(String source) {
    long deleted = embeddingRepository.deleteBySource(source);
    Log.infof("Purged %d embeddings with source=%s", deleted, source);
    return deleted;
  }

  @Transactional
  public long purgeJournals() {
    embeddingRepository.deleteBySource("episodic-consolidator");
    long deleted = Journal.deleteAll();
    Log.infof("Purged %d journals", deleted);
    return deleted;
  }

  @Transactional
  public void purgeAllMemory() {
    embeddingRepository.deleteBySource("semantic-extractor");
    Journal.deleteAll();
    ChatMessageEntity.deleteAll();
    Log.info("Purged all memory: embeddings, journals, chat messages");
  }
}
