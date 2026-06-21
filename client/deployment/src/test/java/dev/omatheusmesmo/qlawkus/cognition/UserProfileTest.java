package dev.omatheusmesmo.qlawkus.cognition;

import dev.omatheusmesmo.qlawkus.store.pg.UserProfileEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class UserProfileTest {

  @Inject
  SoulEngine soulEngine;

  @Inject
  UpdateUserProfileTool tool;

  @AfterEach
  @Transactional
  void reset() {
    UserProfileEntity p = UserProfileEntity.findProfile();
    if (p != null) {
      p.name = null;
      p.profile = null;
    }
  }

  @Test
  @Transactional
  void findProfile_returnsSeededSingleton() {
    UserProfileEntity p = UserProfileEntity.findProfile();
    assertNotNull(p, "migration must seed a UserProfile row");
    assertEquals(1L, p.id);
  }

  @Test
  void systemMessage_alwaysIncludesOwnerContinuityBlock() {
    String msg = soulEngine.getSystemMessage("default").orElseThrow();
    assertTrue(msg.contains("## Your Owner"), msg);
    assertTrue(msg.toLowerCase().contains("same person"), msg);
  }

  @Test
  void emptyProfile_nudgesAgentToRecord() {
    String msg = soulEngine.getSystemMessage("default").orElseThrow();
    assertTrue(msg.contains("updateUserProfile"), msg);
  }

  @Test
  void updatedProfile_isInjectedIntoSystemMessage() {
    tool.updateOwnerName("Matheus");
    tool.updateUserProfile("Works with Java and Quarkus. Prefers constructor injection.");

    String msg = soulEngine.getSystemMessage("default").orElseThrow();
    assertTrue(msg.contains("Matheus"), "owner name should be injected: " + msg);
    assertTrue(msg.contains("constructor injection"), "profile facts should be injected: " + msg);
  }

  @Test
  void updateProfile_rejectsEmpty() {
    String result = tool.updateUserProfile("   ");
    assertTrue(result.toLowerCase().contains("cannot"), result);
  }
}
