package dev.omatheusmesmo.qlawkus.cognition;

import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import dev.omatheusmesmo.qlawkus.store.pg.ChatMessageEntity;
import dev.omatheusmesmo.qlawkus.store.pg.Journal;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class MemoryAdminServiceTest {

  @Inject
  MemoryAdminService adminService;

  @Inject
  EpisodicStore episodicStore;

  @Inject
  WorkingMemoryStore workingMemoryStore;

  @AfterEach
  @Transactional
  void cleanup() {
    Journal.deleteAll();
    ChatMessageEntity.deleteAll();
  }

  @Test
  @Transactional
  void purgeJournals_removesJournals() {
    Journal j1 = new Journal();
    j1.date = LocalDate.of(2026, 4, 20);
    j1.summary = "Summary 1";
    j1.messageCount = 3;
    j1.persist();

    Journal j2 = new Journal();
    j2.date = LocalDate.of(2026, 4, 21);
    j2.summary = "Summary 2";
    j2.messageCount = 5;
    j2.persist();

    long deleted = adminService.purgeJournals();

    assertEquals(2, deleted);
    assertEquals(0, episodicStore.count());
  }

  @Test
  @Transactional
  void purgeAllMemory_clearsJournalsAndChatMessages() {
    Journal j = new Journal();
    j.date = LocalDate.of(2026, 4, 20);
    j.summary = "Summary";
    j.messageCount = 1;
    j.persist();

    ChatMessageEntity.fromChatMessage("s1", dev.langchain4j.data.message.UserMessage.from("hi")).persist();

    adminService.purgeAllMemory();

    assertEquals(0, episodicStore.count());
    assertEquals(0, workingMemoryStore.count());
  }
}
