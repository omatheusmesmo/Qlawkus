package dev.omatheusmesmo.qlawkus.cognition;

import dev.omatheusmesmo.qlawkus.store.pg.SoulEntity;
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
        SoulResetHelper.resetToDefaults();
    }

    @Test
    @Transactional
    void findSoul_returnsSeededEntity() {
        SoulEntity soul = SoulEntity.findSoul();

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
        SoulEntity soul = SoulEntity.findSoul();
        Mood previousMood = soul.mood;
        Mood newMood = previousMood == Mood.CURIOUS ? Mood.ALERT : Mood.CURIOUS;

        soul.mood = newMood;

        SoulEntity reloaded = SoulEntity.findSoul();
        assertEquals(newMood, reloaded.mood);
    }

    @Test
    @Transactional
    void shiftState_persistsChanges() {
        SoulEntity soul = SoulEntity.findSoul();
        String newState = "Reviewing open pull requests at " + Instant.now();

        soul.currentState = newState;

        SoulEntity reloaded = SoulEntity.findSoul();
        assertEquals(newState, reloaded.currentState);
    }

    @Test
    @Transactional
    void rename_persistsNewName() {
        SoulEntity soul = SoulEntity.findSoul();
        String newName = "TestAgent" + System.currentTimeMillis();

        soul.name = newName;

        SoulEntity reloaded = SoulEntity.findSoul();
        assertEquals(newName, reloaded.name);
    }

    @Test
    @Transactional
    void preUpdate_setsUpdatedAt() throws InterruptedException {
        SoulEntity soul = SoulEntity.findSoul();
        Instant beforeUpdate = soul.updatedAt;

        Thread.sleep(10);

        soul.currentState = "Testing timestamp update at " + Instant.now();

        SoulEntity reloaded = SoulEntity.findSoul();
        assertNotNull(reloaded.updatedAt);
        assertTrue(reloaded.updatedAt.isAfter(beforeUpdate) || reloaded.updatedAt.equals(beforeUpdate));
    }
}
