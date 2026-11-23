package dev.omatheusmesmo.qlawkus.store.pg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.OwnerChatStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownOwnerChatStore;
import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@QuarkusTest
class OwnerChatStoresTest {

  private static final Instant SEEN = Instant.parse("2026-11-01T10:00:00Z");
  private static final OwnerChat OLD_DISCORD = new OwnerChat("discord", "old", SEEN);
  private static final OwnerChat NEW_DISCORD = new OwnerChat("discord", "new", SEEN.plusSeconds(60));
  private static final OwnerChat TELEGRAM = new OwnerChat("telegram", "first", SEEN);

  @TempDir
  Path directory;

  @Inject
  OwnerChatStore store;

  @Inject
  DataSource dataSource;

  @Inject
  OwnerChatRepository repository;

  @Inject
  PgWriteProbe probe;

  @BeforeEach
  @AfterEach
  void emptyTable() throws Exception {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement("DELETE FROM owner_chat")) {
      statement.executeUpdate();
    }
  }

  @Test
  void pgvectorKeepsTheLatestObservationAndChecksItsTable() {
    assertInstanceOf(PgOwnerChatStore.class, store);
    store.save(NEW_DISCORD);
    store.save(OLD_DISCORD);
    store.verifyWritable();

    assertEquals(List.of(NEW_DISCORD), store.loadAll());
  }

  @Test
  void pgvectorRecoversLegacyFilesWithoutReplacingNewerRowsOrRewritingFiles() {
    var files = markdown();
    files.save(OLD_DISCORD);
    files.save(TELEGRAM);
    store.save(NEW_DISCORD);
    var recovered = new PgOwnerChatStore(config(), repository, probe);

    assertEquals(List.of(NEW_DISCORD, TELEGRAM), recovered.loadAll());
    assertEquals(List.of(NEW_DISCORD, TELEGRAM), store.loadAll());
    assertEquals(List.of(OLD_DISCORD, TELEGRAM), files.loadAll());
    assertEquals(List.of(NEW_DISCORD, TELEGRAM), recovered.loadAll());
  }

  @Test
  void hybridRepairsBothSidesAndKeepsTheirNewestObservations() {
    var files = markdown();
    files.save(OLD_DISCORD);
    files.save(TELEGRAM);
    store.save(NEW_DISCORD);
    store.save(new OwnerChat("telegram", "older", SEEN.minusSeconds(60)));
    var hybrid = new HybridOwnerChatStore(config(), repository, probe);

    assertEquals(List.of(NEW_DISCORD, TELEGRAM), hybrid.loadAll());
    assertEquals(List.of(NEW_DISCORD, TELEGRAM), files.loadAll());
    assertEquals(List.of(NEW_DISCORD, TELEGRAM), store.loadAll());

    var updated = new OwnerChat("telegram", "second", SEEN.plusSeconds(120));
    hybrid.save(updated);
    hybrid.verifyWritable();
    assertEquals(List.of(NEW_DISCORD, updated), files.loadAll());
    assertEquals(List.of(NEW_DISCORD, updated), store.loadAll());
  }

  @Test
  void aFilesystemFailureCanStillPersistAChatInPostgreSQL() throws Exception {
    Files.createFile(directory.resolve(MarkdownOwnerChatStore.DIRECTORY));
    var hybrid = new HybridOwnerChatStore(config(), repository, probe);

    assertThrows(RuntimeException.class, () -> hybrid.save(NEW_DISCORD));
    assertThrows(RuntimeException.class, hybrid::verifyWritable);
    assertEquals(List.of(NEW_DISCORD), hybrid.loadAll());
  }

  private AgentConfig config() {
    AgentConfig config = mock(AgentConfig.class, RETURNS_DEEP_STUBS);
    when(config.state().root()).thenReturn(directory.toString());
    return config;
  }

  private MarkdownOwnerChatStore markdown() {
    return new MarkdownOwnerChatStore(directory.resolve(MarkdownOwnerChatStore.DIRECTORY));
  }
}
