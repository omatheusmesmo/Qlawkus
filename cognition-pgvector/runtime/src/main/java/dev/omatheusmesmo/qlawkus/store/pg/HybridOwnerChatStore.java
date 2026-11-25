package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.OwnerChatStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownOwnerChatStore;
import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
@Typed(HybridOwnerChatStore.class)
public class HybridOwnerChatStore implements OwnerChatStore {

  private final MarkdownOwnerChatStore files;
  private final OwnerChatRepository repository;
  private final PgWriteProbe writeProbe;

  @Inject
  public HybridOwnerChatStore(AgentConfig config, OwnerChatRepository repository, PgWriteProbe writeProbe) {
    this(Path.of(config.state().root(), MarkdownOwnerChatStore.DIRECTORY), repository, writeProbe);
  }

  HybridOwnerChatStore(Path directory, OwnerChatRepository repository, PgWriteProbe writeProbe) {
    this.files = new MarkdownOwnerChatStore(directory);
    this.repository = repository;
    this.writeProbe = writeProbe;
  }

  @Override
  public void save(OwnerChat chat) {
    try {
      files.save(chat);
    } catch (RuntimeException fileFailure) {
      try {
        repository.save(chat, true);
      } catch (RuntimeException databaseFailure) {
        fileFailure.addSuppressed(databaseFailure);
      }
      throw fileFailure;
    }
    repository.save(chat, true);
  }

  @Override
  public List<OwnerChat> loadAll() {
    return OwnerChatRecovery.load(files, repository, true);
  }

  @Override
  public void verifyWritable() {
    files.verifyWritable();
    writeProbe.verify(PgWriteProbe.Table.OWNER_CHAT);
  }
}
