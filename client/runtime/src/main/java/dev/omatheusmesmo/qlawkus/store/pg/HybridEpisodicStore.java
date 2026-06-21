package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownEpisodicFiles;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Hybrid {@link EpisodicStore}, active when {@code qlawkus.cognition.backend=hybrid}. Journals are
 * written to dated {@code <date>.md} files (the editable, git-versionable source of truth) and
 * mirrored into the {@code Journal} table; reads run against the table so summaries carry real
 * database ids. Selected at build time via {@link IfBuildProperty}.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.cognition.backend", stringValue = "hybrid")
public class HybridEpisodicStore implements EpisodicStore {

  private final MarkdownEpisodicFiles files;
  private final JournalRepository journals;

  @Inject
  public HybridEpisodicStore(AgentConfig config, JournalRepository journals) {
    this(config.episodic().root(), journals);
  }

  HybridEpisodicStore(String root, JournalRepository journals) {
    this.files = new MarkdownEpisodicFiles(Path.of(root));
    this.journals = journals;
  }

  @Override
  public boolean existsForDate(LocalDate date) {
    return journals.existsForDate(date);
  }

  @Override
  public void storeEpisode(LocalDate date, String summary, int messageCount) {
    if (journals.existsForDate(date)) {
      Log.debugf("Journal already exists for %s, skipping", date);
      return;
    }
    files.write(date, summary, messageCount);
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
    files.deleteAll();
    return journals.deleteAll();
  }
}
