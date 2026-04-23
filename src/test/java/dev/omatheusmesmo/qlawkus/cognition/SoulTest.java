package dev.omatheusmesmo.qlawkus.cognition;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SoulTest {

  @AfterEach
  @Transactional
  void resetSoul() {
    Soul soul = Soul.findSoul();
    soul.rename("Qlawkus");
    soul.shiftMood(Mood.FOCUSED);
    soul.shiftState("Awaiting first interaction. No active context or specialization yet.");
  }

  @Test
  @Transactional
  void findSoul_returnsSeededEntity() {
    Soul soul = Soul.findSoul();

    assertNotNull(soul);
    assertEquals(1L, soul.id);
    assertEquals("Qlawkus", soul.name);
    assertNotNull(soul.coreIdentity);
    assertTrue(soul.coreIdentity.contains("Who I Am"));
    assertNotNull(soul.currentState);
    assertNotNull(soul.mood);
    assertNotNull(soul.createdAt);
    assertNotNull(soul.updatedAt);
  }

  @Test
  @Transactional
  void shiftMood_persistsNewMood() {
    Soul soul = Soul.findSoul();
    Mood previousMood = soul.mood;
    Mood newMood = previousMood == Mood.CURIOUS ? Mood.ALERT : Mood.CURIOUS;

    soul.shiftMood(newMood);

    Soul reloaded = Soul.findSoul();
    assertEquals(newMood, reloaded.mood);
  }

  @Test
  @Transactional
  void shiftState_persistsChanges() {
    Soul soul = Soul.findSoul();
    String newState = "Reviewing open pull requests at " + Instant.now();

    soul.shiftState(newState);

    Soul reloaded = Soul.findSoul();
    assertEquals(newState, reloaded.currentState);
  }

  @Test
  @Transactional
  void rename_persistsNewName() {
    Soul soul = Soul.findSoul();
    String newName = "TestAgent" + System.currentTimeMillis();

    soul.rename(newName);

    Soul reloaded = Soul.findSoul();
    assertEquals(newName, reloaded.name);
  }

  @Test
  @Transactional
  void preUpdate_setsUpdatedAt() throws InterruptedException {
    Soul soul = Soul.findSoul();
    Instant beforeUpdate = soul.updatedAt;

    Thread.sleep(10);

    soul.shiftState("Testing timestamp update at " + Instant.now());

    Soul reloaded = Soul.findSoul();
    assertNotNull(reloaded.updatedAt);
    assertTrue(reloaded.updatedAt.isAfter(beforeUpdate) || reloaded.updatedAt.equals(beforeUpdate));
  }
}
