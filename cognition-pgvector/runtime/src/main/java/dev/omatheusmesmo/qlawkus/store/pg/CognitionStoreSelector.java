package dev.omatheusmesmo.qlawkus.store.pg;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.omatheusmesmo.qlawkus.approval.ApprovalStore;
import dev.omatheusmesmo.qlawkus.approval.FileApprovalStore;
import dev.omatheusmesmo.qlawkus.skill.MarkdownSkillStore;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.OwnerChatStore;
import dev.omatheusmesmo.qlawkus.store.ScheduledTaskStore;
import dev.omatheusmesmo.qlawkus.store.SoulStore;
import dev.omatheusmesmo.qlawkus.store.UserProfileStore;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownEpisodicStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownFactStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownOwnerChatStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownScheduledTaskStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownSoulStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownUserProfileStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownWorkingMemoryStore;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

/**
 * Picks the store behind each cognition SPI when the application boots, from
 * {@code qlawkus.cognition.backend}. The markdown, Postgres and hybrid implementations are all beans
 * (the latter two typed to their own class so they never answer the SPI directly), and the one that is
 * not chosen is never created: it is reached only through an {@link Instance}, so a database-free boot
 * never touches a datasource it does not have. The same jar therefore serves the quickstart on files
 * and a Postgres deployment.
 */
@ApplicationScoped
@Unremovable
public class CognitionStoreSelector {

  private final CognitionBackendState state;

  CognitionStoreSelector(CognitionBackendState state) {
    this.state = state;
  }

  @Produces
  @ApplicationScoped
  FactStore facts(Instance<MarkdownFactStore> markdown, Instance<PgFactStore> postgres,
      Instance<HybridFactStore> hybrid) {
    return pick(markdown, postgres, hybrid);
  }

  @Produces
  @ApplicationScoped
  EpisodicStore episodes(Instance<MarkdownEpisodicStore> markdown, Instance<PgEpisodicStore> postgres,
      Instance<HybridEpisodicStore> hybrid) {
    return pick(markdown, postgres, hybrid);
  }

  @Produces
  @ApplicationScoped
  SoulStore soul(Instance<MarkdownSoulStore> markdown, Instance<PgSoulStore> postgres,
      Instance<HybridSoulStore> hybrid) {
    return pick(markdown, postgres, hybrid);
  }

  @Produces
  @ApplicationScoped
  UserProfileStore profile(Instance<MarkdownUserProfileStore> markdown, Instance<PgUserProfileStore> postgres,
      Instance<HybridUserProfileStore> hybrid) {
    return pick(markdown, postgres, hybrid);
  }

  @Produces
  @ApplicationScoped
  OwnerChatStore ownerChat(Instance<MarkdownOwnerChatStore> markdown, Instance<PgOwnerChatStore> postgres,
      Instance<HybridOwnerChatStore> hybrid) {
    return pick(markdown, postgres, hybrid);
  }

  @Produces
  @ApplicationScoped
  ScheduledTaskStore tasks(Instance<MarkdownScheduledTaskStore> markdown,
      Instance<PgScheduledTaskStore> postgres, Instance<HybridScheduledTaskStore> hybrid) {
    return pick(markdown, postgres, hybrid);
  }

  @Produces
  @ApplicationScoped
  SkillStore skills(Instance<MarkdownSkillStore> markdown, Instance<PgSkillStore> postgres,
      Instance<HybridSkillStore> hybrid) {
    return pick(markdown, postgres, hybrid);
  }

  @Produces
  @ApplicationScoped
  ApprovalStore approvals(Instance<FileApprovalStore> markdown, Instance<PgApprovalStore> postgres,
      Instance<HybridApprovalStore> hybrid) {
    return pick(markdown, postgres, hybrid);
  }

  @Produces
  @ApplicationScoped
  WorkingMemoryStore workingMemory(Instance<MarkdownWorkingMemoryStore> markdown,
      Instance<PgWorkingMemoryStore> postgres, Instance<HybridWorkingMemoryStore> hybrid) {
    return pick(markdown, postgres, hybrid);
  }

  @Produces
  @ApplicationScoped
  ChatMemoryStore chatMemory(Instance<MarkdownWorkingMemoryStore> markdown,
      Instance<PgWorkingMemoryStore> postgres, Instance<HybridWorkingMemoryStore> hybrid) {
    return pick(markdown, postgres, hybrid);
  }

  private <T> T pick(Instance<? extends T> markdown, Instance<? extends T> postgres,
      Instance<? extends T> hybrid) {
    return switch (state.backend()) {
      case MARKDOWN -> markdown.get();
      case PGVECTOR -> postgres.get();
      case HYBRID -> hybrid.get();
    };
  }
}
