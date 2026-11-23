package dev.omatheusmesmo.qlawkus.store.pg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownOwnerChatStore;
import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OwnerChatRecoveryTest {

  @TempDir
  Path directory;

  @Test
  void anUnavailableDatabaseStillRecoversTheFileWithoutWaitingForMoreConnections() {
    var chat = new OwnerChat("discord", "fixture", Instant.parse("2026-11-01T10:00:00Z"));
    new MarkdownOwnerChatStore(directory).save(chat);
    var unavailable = mock(OwnerChatRepository.class);
    when(unavailable.listAll()).thenThrow(new IllegalStateException("test database outage"));

    assertEquals(List.of(chat), new HybridOwnerChatStore(directory, unavailable, mock(PgWriteProbe.class)).loadAll());
    org.mockito.Mockito.verify(unavailable).listAll();
    verifyNoMoreInteractions(unavailable);
  }
}
