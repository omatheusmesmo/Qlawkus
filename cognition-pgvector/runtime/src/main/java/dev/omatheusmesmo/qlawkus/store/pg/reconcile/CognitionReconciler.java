package dev.omatheusmesmo.qlawkus.store.pg.reconcile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.omatheusmesmo.qlawkus.cognition.Soul;
import dev.omatheusmesmo.qlawkus.cognition.UserProfile;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.config.SkillsConfig;
import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillState;
import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import dev.omatheusmesmo.qlawkus.store.SoulStore;
import dev.omatheusmesmo.qlawkus.store.UserProfileStore;
import dev.omatheusmesmo.qlawkus.skill.MarkdownSkillFiles;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownEpisodicFiles;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownEpisodicFiles.JournalRecord;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownFactFiles;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownFactFiles.FactRecord;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownSoulStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownUserProfileStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownWorkingMemoryFiles;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownWorkingMemoryFiles.StoredMessage;
import dev.omatheusmesmo.qlawkus.store.pg.ChatMessageEntity;
import dev.omatheusmesmo.qlawkus.store.pg.EmbeddingRepository;
import dev.omatheusmesmo.qlawkus.store.pg.EmbeddingRepository.FactRow;
import dev.omatheusmesmo.qlawkus.store.pg.JournalRepository;
import dev.omatheusmesmo.qlawkus.store.pg.SkillEntity;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Reconciles the markdown file representation of cognition data with its pgvector representation.
 * Lives in the qlawkus-cognition-pgvector extension because it needs both the file helpers (from
 * qlawkus-client) and the Postgres repositories at once, so it can only run in a build that includes
 * this extension ({@code pgvector} or {@code hybrid}).
 *
 * <p>Collections (facts, journals, skills, working-memory conversations) reconcile as a bidirectional,
 * idempotent union keyed by a stable identity (content hash, date, name, conversation id); re-running
 * is a no-op. The singletons (persona, owner profile) are filled only when one side is empty, so a
 * reconcile never clobbers a customized side; use {@link CognitionMigrator} for an explicit
 * directional overwrite.
 */
@ApplicationScoped
public class CognitionReconciler {

  private final AgentConfig config;
  private final SkillsConfig skillsConfig;
  private final SoulStore soulStore;
  private final UserProfileStore userProfileStore;
  private final EmbeddingModel embeddingModel;
  private final EmbeddingStore<TextSegment> embeddingStore;
  private final EmbeddingRepository embeddingRepository;
  private final JournalRepository journalRepository;
  private final ObjectMapper objectMapper;

  @Inject
  public CognitionReconciler(AgentConfig config, SkillsConfig skillsConfig, SoulStore soulStore,
      UserProfileStore userProfileStore, EmbeddingModel embeddingModel,
      EmbeddingStore<TextSegment> embeddingStore, EmbeddingRepository embeddingRepository,
      JournalRepository journalRepository, ObjectMapper objectMapper) {
    this.config = config;
    this.skillsConfig = skillsConfig;
    this.soulStore = soulStore;
    this.userProfileStore = userProfileStore;
    this.embeddingModel = embeddingModel;
    this.embeddingStore = embeddingStore;
    this.embeddingRepository = embeddingRepository;
    this.journalRepository = journalRepository;
    this.objectMapper = objectMapper;
  }

  /** Counts of records copied into each side during a reconcile/migrate pass. */
  public record Stats(long toPg, long toFiles) {
    public static final Stats EMPTY = new Stats(0, 0);

    public Stats plus(Stats other) {
      return new Stats(toPg + other.toPg, toFiles + other.toFiles);
    }
  }

  /** Which way data flows. {@code BOTH} is the non-destructive union used by reconciliation. */
  public enum Direction {
    FILES_TO_PG, PG_TO_FILES, BOTH;

    boolean toPg() {
      return this == FILES_TO_PG || this == BOTH;
    }

    boolean toFiles() {
      return this == PG_TO_FILES || this == BOTH;
    }
  }

  /** Bidirectional union of every store. Idempotent. */
  public Stats reconcileAll() {
    Stats stats = reconcile(Direction.BOTH);
    Log.infof("Cognition reconcile complete: +%d to pg, +%d to files", stats.toPg(), stats.toFiles());
    return stats;
  }

  /** Runs the chosen direction across all stores; the engine behind both reconcile and migrate. */
  public Stats reconcile(Direction direction) {
    return reconcileFacts(direction)
        .plus(reconcileJournals(direction))
        .plus(reconcileSkills(direction))
        .plus(reconcileWorkingMemory(direction))
        .plus(reconcileSoul(direction))
        .plus(reconcileProfile(direction));
  }

  Stats reconcileFacts(Direction direction) {
    MarkdownFactFiles files = new MarkdownFactFiles(Path.of(config.facts().root()));
    long toPg = 0;
    long toFiles = 0;
    if (direction.toPg()) {
      for (FactRecord fact : files.loadAll()) {
        if (!embeddingRepository.existsByContentHash(EmbeddingRepository.md5(fact.content()))) {
          addFactToPg(fact.content(), fact.metadata());
          toPg++;
        }
      }
    }
    if (direction.toFiles()) {
      for (FactRow row : embeddingRepository.loadAllFacts()) {
        String id = EmbeddingRepository.md5(row.text());
        if (!files.exists(id)) {
          files.write(id, row.text(), parseMetadata(row.metadataJson()));
          toFiles++;
        }
      }
    }
    return logged("facts", toPg, toFiles);
  }

  Stats reconcileJournals(Direction direction) {
    MarkdownEpisodicFiles files = new MarkdownEpisodicFiles(Path.of(config.episodic().root()));
    long toPg = 0;
    long toFiles = 0;
    if (direction.toPg()) {
      for (JournalRecord journal : files.loadAll()) {
        if (!journalRepository.existsForDate(journal.date())) {
          journalRepository.store(journal.date(), journal.summary(), journal.messageCount());
          toPg++;
        }
      }
    }
    if (direction.toFiles()) {
      for (JournalSummary journal : journalRepository.listJournals()) {
        if (!files.exists(journal.date())) {
          files.write(journal.date(), journal.summary(), journal.messageCount());
          toFiles++;
        }
      }
    }
    return logged("journals", toPg, toFiles);
  }

  Stats reconcileSkills(Direction direction) {
    MarkdownSkillFiles files = new MarkdownSkillFiles(skillRoots());
    long[] counts = new long[2];
    QuarkusTransaction.requiringNew().run(() -> {
      if (direction.toPg()) {
        for (SkillSummary summary : files.index()) {
          if (SkillEntity.findById(summary.name()) == null) {
            files.get(summary.name())
                .ifPresent(skill -> SkillEntity.upsert(skill.name(),
                    skill.description() == null ? "" : skill.description(),
                    skill.body() == null ? "" : skill.body()));
            counts[0]++;
          }
        }
      }
      if (direction.toFiles()) {
        List<SkillEntity> owned = SkillEntity.list("state <> ?1", SkillState.ARCHIVED);
        for (SkillEntity entity : owned) {
          if (files.get(entity.name).isEmpty()) {
            files.save(new Skill(entity.name, entity.description, entity.body));
            counts[1]++;
          }
        }
      }
    });
    return logged("skills", counts[0], counts[1]);
  }

  Stats reconcileWorkingMemory(Direction direction) {
    MarkdownWorkingMemoryFiles files =
        new MarkdownWorkingMemoryFiles(Path.of(config.workingMemory().root()));
    long[] counts = new long[2];
    QuarkusTransaction.requiringNew().run(() -> {
      if (direction.toPg()) {
        for (String memoryId : files.listMemoryIds()) {
          if (ChatMessageEntity.findByMemoryIdOrdered(memoryId).isEmpty()) {
            for (StoredMessage stored : files.read(memoryId)) {
              ChatMessage message = ChatMessageDeserializer.messageFromJson(stored.messageJson());
              ChatMessageEntity.fromChatMessage(memoryId, message).persist();
            }
            counts[0]++;
          }
        }
      }
      if (direction.toFiles()) {
        for (String memoryId : ChatMessageEntity.distinctMemoryIds()) {
          if (!files.exists(memoryId)) {
            List<String> jsons = ChatMessageEntity.findByMemoryIdOrdered(memoryId).stream()
                .map(entity -> ChatMessageSerializer.messageToJson(entity.toChatMessage()))
                .toList();
            files.replace(memoryId, jsons);
            counts[1]++;
          }
        }
      }
    });
    return logged("working-memory", counts[0], counts[1]);
  }

  Stats reconcileSoul(Direction direction) {
    String root = config.state().root();
    boolean fileExists = Files.isRegularFile(Path.of(root, "soul.md"));
    MarkdownSoulStore fileStore = new MarkdownSoulStore(root);
    Soul pgSoul = soulStore.load();
    long toPg = 0;
    long toFiles = 0;
    switch (direction) {
      case FILES_TO_PG -> {
        if (fileExists) {
          soulStore.save(fileStore.load());
          toPg = 1;
        }
      }
      case PG_TO_FILES -> {
        if (pgSoul != null) {
          fileStore.save(pgSoul);
          toFiles = 1;
        }
      }
      // Non-destructive: persona is always seeded in pg, so only fill an absent file. Importing a
      // markdown persona into pg is an explicit migrate (FILES_TO_PG) to avoid clobbering the seed.
      case BOTH -> {
        if (!fileExists && pgSoul != null) {
          fileStore.save(pgSoul);
          toFiles = 1;
        }
      }
    }
    return logged("soul", toPg, toFiles);
  }

  Stats reconcileProfile(Direction direction) {
    String root = config.state().root();
    boolean fileExists = Files.isRegularFile(Path.of(root, "owner.md"));
    MarkdownUserProfileStore fileStore = new MarkdownUserProfileStore(root);
    UserProfile pgProfile = userProfileStore.load();
    UserProfile fileProfile = fileExists ? fileStore.load() : null;
    long toPg = 0;
    long toFiles = 0;
    switch (direction) {
      case FILES_TO_PG -> {
        if (fileExists) {
          userProfileStore.save(fileProfile);
          toPg = 1;
        }
      }
      case PG_TO_FILES -> {
        if (!isBlank(pgProfile)) {
          fileStore.save(pgProfile);
          toFiles = 1;
        }
      }
      case BOTH -> {
        if (isBlank(pgProfile) && !isBlank(fileProfile)) {
          userProfileStore.save(fileProfile);
          toPg = 1;
        } else if (!isBlank(pgProfile) && (fileProfile == null || isBlank(fileProfile))) {
          fileStore.save(pgProfile);
          toFiles = 1;
        }
      }
    }
    return logged("profile", toPg, toFiles);
  }

  private List<Path> skillRoots() {
    return skillsConfig.roots().stream().map(Path::of).toList();
  }

  private void addFactToPg(String content, Map<String, String> metadata) {
    Metadata segmentMetadata = new Metadata();
    metadata.forEach((key, value) -> segmentMetadata.put(key, value == null ? "" : value));
    Embedding embedding = embeddingModel.embed(content).content();
    embeddingStore.add(embedding, TextSegment.from(content, segmentMetadata));
  }

  private Map<String, String> parseMetadata(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      Log.warnf(e, "Failed to parse fact metadata JSON, writing fact without metadata: %s", json);
      return Map.of();
    }
  }

  private static boolean isBlank(UserProfile profile) {
    return profile == null || profile.profile == null || profile.profile.isBlank();
  }

  private static Stats logged(String store, long toPg, long toFiles) {
    if (toPg > 0 || toFiles > 0) {
      Log.infof("Reconciled %s: +%d pg, +%d files", store, toPg, toFiles);
    }
    return new Stats(toPg, toFiles);
  }
}
