package dev.omatheusmesmo.qlawkus.store.pg;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * Rebuilds the vectors that {@code R__embedding_dimension} cleared when the embedding model changed
 * dimension, from the text every row keeps. Without it a model change, like NVIDIA retiring its 1024
 * model (#357), would leave every stored fact, journal and transcript unreachable by retrieval.
 *
 * <p>It starts after boot on its own thread rather than inside startup: re-embedding a large store
 * takes as long as the provider does, and readiness should not wait for it. Retrieval keeps working
 * meanwhile, since a row without a vector simply never matches. It runs on the {@code batch}
 * workload, so a provider that throttles it cannot open the breaker the owner's next message uses.
 *
 * <p>It is re-runnable by construction: it only ever selects rows that still lack a vector, so a run
 * cut short by a failure or a shutdown resumes where it stopped at the next boot.
 */
@ApplicationScoped
public class EmbeddingBackfill {

  static final String JOB = "embedding-backfill";
  private static final int BATCH = 32;

  @Inject
  DataSource dataSource;

  @Inject
  EmbeddingModel embeddingModel;

  @Inject
  AgentMeters meters;

  void onStart(@Observes StartupEvent event) {
    Thread.ofVirtual().name(JOB).start(() -> {
      try {
        WorkloadContext.runAs(WorkloadContext.BATCH, () -> meters.timeJob(JOB, this::reembedMissing));
      } catch (RuntimeException e) {
        Log.warnf(e, "Re-embedding stored vectors stopped; the rest resumes at the next boot");
      }
    });
  }

  /** Re-embeds every row without a vector, in batches; returns how many it rebuilt. */
  public long reembedMissing() {
    long rebuilt = 0;
    for (List<Row> rows = missing(); !rows.isEmpty(); rows = missing()) {
      List<Embedding> vectors = embeddingModel.embedAll(
          rows.stream().map(row -> TextSegment.from(row.text())).toList()).content();
      write(rows, vectors);
      rebuilt += rows.size();
    }
    if (rebuilt > 0) {
      Log.infof("Re-embedded %d stored vectors for the current embedding model", rebuilt);
    }
    return rebuilt;
  }

  private List<Row> missing() {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "SELECT embedding_id, text FROM embeddings WHERE embedding IS NULL LIMIT " + BATCH);
        ResultSet result = statement.executeQuery()) {
      List<Row> rows = new ArrayList<>();
      while (result.next()) {
        rows.add(new Row(result.getObject(1, UUID.class), result.getString(2)));
      }
      return rows;
    } catch (SQLException e) {
      throw new IllegalStateException("Could not read the vectors left to re-embed", e);
    }
  }

  private void write(List<Row> rows, List<Embedding> vectors) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "UPDATE embeddings SET embedding = ?::vector WHERE embedding_id = ?")) {
      for (int i = 0; i < rows.size(); i++) {
        statement.setString(1, literal(vectors.get(i).vector()));
        statement.setObject(2, rows.get(i).id());
        statement.addBatch();
      }
      statement.executeBatch();
    } catch (SQLException e) {
      throw new IllegalStateException("Could not store re-embedded vectors", e);
    }
  }

  private static String literal(float[] vector) {
    List<String> parts = new ArrayList<>(vector.length);
    for (float component : vector) {
      parts.add(Float.toString(component));
    }
    return parts.stream().collect(Collectors.joining(",", "[", "]"));
  }

  private record Row(UUID id, String text) {
  }
}
