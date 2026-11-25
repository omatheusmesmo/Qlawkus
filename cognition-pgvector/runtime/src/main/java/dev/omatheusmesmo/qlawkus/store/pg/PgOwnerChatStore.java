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
@Typed(PgOwnerChatStore.class)
public class PgOwnerChatStore implements OwnerChatStore {

  private final MarkdownOwnerChatStore files;
  private final OwnerChatRepository repository;
  private final PgWriteProbe writeProbe;

  @Inject
  public PgOwnerChatStore(AgentConfig config, OwnerChatRepository repository, PgWriteProbe writeProbe) {
    this(Path.of(config.state().root(), MarkdownOwnerChatStore.DIRECTORY), repository, writeProbe);
  }

  PgOwnerChatStore(Path directory, OwnerChatRepository repository, PgWriteProbe writeProbe) {
    this.files = new MarkdownOwnerChatStore(directory);
    this.repository = repository;
    this.writeProbe = writeProbe;
  }

  @Override
  public void save(OwnerChat chat) {
    repository.save(chat, true);
  }

  @Override
  public List<OwnerChat> loadAll() {
    return OwnerChatRecovery.load(files, repository, false);
  }

  @Override
  public void verifyWritable() {
    writeProbe.verify(PgWriteProbe.Table.OWNER_CHAT);
  }
}
