package dev.omatheusmesmo.qlawkus.store.markdown;

import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Markdown-backed {@link EpisodicStore}, active when {@code qlawkus.cognition.backend=markdown}.
 * Each daily journal is a {@code <root>/<date>.md} file under {@code qlawkus.agent.episodic.root};
 * the date is the filename, so there is one journal per day and a repeated consolidation is a no-op.
 * No database is used. The journal text is still embedded for recall through the markdown
 * {@code FactStore} ({@code source=episodic-consolidator}), which owns its own index.
 */
@ApplicationScoped
@DefaultBean
public class MarkdownEpisodicStore implements EpisodicStore {

  private final MarkdownEpisodicFiles files;

  @Inject
  public MarkdownEpisodicStore(AgentConfig config) {
    this(config.episodic().root());
  }

  public MarkdownEpisodicStore(String root) {
    this.files = new MarkdownEpisodicFiles(Path.of(root));
  }

  @Override
  public boolean existsForDate(LocalDate date) {
    return files.exists(date);
  }

  @Override
  public void storeEpisode(LocalDate date, String summary, int messageCount) {
    if (files.exists(date)) {
      Log.debugf("Journal already exists for %s, skipping", date);
      return;
    }
    files.write(date, summary, messageCount);
  }

  @Override
  public List<JournalSummary> listJournals() {
    return files.loadAll().stream()
        .map(j -> new JournalSummary(null, j.date(), j.summary(), j.messageCount(), j.createdAt()))
        .toList();
  }

  @Override
  public long count() {
    return files.count();
  }

  @Override
  public long purgeAll() {
    return files.deleteAll();
  }
}
