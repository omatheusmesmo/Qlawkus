package dev.omatheusmesmo.qlawkus.it.cognition;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.UserMessage;
import dev.omatheusmesmo.qlawkus.cognition.Soul;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.config.SkillsConfig;
import dev.omatheusmesmo.qlawkus.skill.MarkdownSkillFiles;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.SoulStore;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownEpisodicFiles;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownFactFiles;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownSoulStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownWorkingMemoryFiles;
import dev.omatheusmesmo.qlawkus.store.pg.EmbeddingRepository;
import dev.omatheusmesmo.qlawkus.store.pg.JournalRepository;
import dev.omatheusmesmo.qlawkus.store.pg.reconcile.CognitionMigrator;
import dev.omatheusmesmo.qlawkus.store.pg.reconcile.CognitionReconciler;
import dev.omatheusmesmo.qlawkus.store.pg.reconcile.CognitionReconciler.Direction;
import dev.omatheusmesmo.qlawkus.store.pg.reconcile.CognitionReconciler.Stats;
import dev.omatheusmesmo.qlawkus.testing.QlawkusWireMockStubs;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates that the cognition reconciler/migrator move data between the markdown files and pgvector
 * in both directions and idempotently. Runs in the cognition IT (pgvector backend + Dev Services
 * Postgres + a stubbed embedding endpoint).
 */
@QuarkusTest
@ConnectWireMock
class CognitionReconcileTest {

  private static final LocalDate JOURNAL_DATE = LocalDate.of(2026, 1, 2);
  private static final String CONVERSATION = "recon-conv";
  private static final String SKILL = "recon-skill";

  WireMock wiremock;

  @Inject
  CognitionReconciler reconciler;
  @Inject
  CognitionMigrator migrator;
  @Inject
  EmbeddingRepository embeddingRepository;
  @Inject
  JournalRepository journalRepository;
  @Inject
  FactStore factStore;
  @Inject
  EpisodicStore episodicStore;
  @Inject
  WorkingMemoryStore workingMemoryStore;
  @Inject
  SkillStore skillStore;
  @Inject
  SoulStore soulStore;
  @Inject
  AgentConfig config;
  @Inject
  SkillsConfig skillsConfig;

  @BeforeEach
  void reset() throws IOException {
    QlawkusWireMockStubs.registerOpenAiStubs(wiremock);
    factStore.purgeAll();
    episodicStore.purgeAll();
    workingMemoryStore.purgeAll();
    skillStore.delete(SKILL);
    skillFiles().delete(SKILL);
    deleteTree(Path.of(config.facts().root()).getParent());
  }

  @Test
  void filesBackfillIntoPgOnReconcile() {
    factFiles().write(EmbeddingRepository.md5("Owner prefers Java"), "Owner prefers Java",
        Map.of("source", "remember-tool"));
    journalFiles().write(JOURNAL_DATE, "A day of refactoring.", 3);
    wmFiles().append(CONVERSATION,
        List.of(ChatMessageSerializer.messageToJson(UserMessage.from("hello"))));
    skillFiles().save(new Skill(SKILL, "test skill", "do the thing"));

    reconciler.reconcileAll();

    assertTrue(embeddingRepository.existsByContentHash(EmbeddingRepository.md5("Owner prefers Java")),
        "fact should be mirrored into pgvector");
    assertTrue(journalRepository.existsForDate(JOURNAL_DATE), "journal should be mirrored into pg");
    assertFalse(workingMemoryStore.getMessages(CONVERSATION).isEmpty(),
        "working-memory conversation should be mirrored into pg");
    assertTrue(skillStore.get(SKILL).isPresent(), "skill should be mirrored into pg");
  }

  @Test
  void pgBackfillsIntoFilesOnReconcile() {
    journalRepository.store(JOURNAL_DATE, "Stored straight into pg.", 5);

    reconciler.reconcileAll();

    assertTrue(journalFiles().exists(JOURNAL_DATE), "pg journal should be written to a file");
  }

  @Test
  void reconcileIsIdempotent() {
    journalFiles().write(JOURNAL_DATE, "Once.", 1);
    reconciler.reconcileAll();

    Stats second = reconciler.reconcileAll();

    assertEquals(0, second.toPg(), "no records should be re-added to pg on a second pass");
    assertEquals(0, second.toFiles(), "no records should be re-added to files on a second pass");
  }

  @Test
  void migrateFilesToPgOverwritesPersona() {
    MarkdownSoulStore fileSoul = new MarkdownSoulStore(config.state().root());
    Soul soul = fileSoul.load();
    soul.rename("Reconciled");
    fileSoul.save(soul);

    migrator.migrate(Direction.FILES_TO_PG);

    assertEquals("Reconciled", soulStore.load().name,
        "migrate files-to-pg should overwrite the persona in pg");
  }

  private MarkdownFactFiles factFiles() {
    return new MarkdownFactFiles(Path.of(config.facts().root()));
  }

  private MarkdownEpisodicFiles journalFiles() {
    return new MarkdownEpisodicFiles(Path.of(config.episodic().root()));
  }

  private MarkdownWorkingMemoryFiles wmFiles() {
    return new MarkdownWorkingMemoryFiles(Path.of(config.workingMemory().root()));
  }

  private MarkdownSkillFiles skillFiles() {
    return new MarkdownSkillFiles(List.of(Path.of(skillsConfig.roots().get(0))));
  }

  private static void deleteTree(Path root) throws IOException {
    if (root == null || !Files.exists(root)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(root)) {
      walk.sorted(Comparator.reverseOrder()).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }
  }
}
