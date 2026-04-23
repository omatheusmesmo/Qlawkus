package dev.omatheusmesmo.qlawkus.cognition;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class UpdateSelfStateToolTest {

  @Inject
  UpdateSelfStateTool updateSelfStateTool;

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
    void updateCurrentState_persistsNewState() {
        String newState = "Reviewing pull requests at " + System.currentTimeMillis();

        String result = updateSelfStateTool.updateCurrentState(newState);

        assertTrue(result.contains("Current state updated"));
        Soul soul = Soul.findSoul();
        assertEquals(newState, soul.currentState);
    }

    @Test
    @Transactional
    void updateMood_persistsValidMood() {
        String result = updateSelfStateTool.updateMood("CURIOUS");

        assertTrue(result.contains("Mood updated to: CURIOUS"));
        Soul soul = Soul.findSoul();
        assertEquals(Mood.CURIOUS, soul.mood);
    }

    @Test
    @Transactional
    void updateMood_rejectsInvalidMood() {
        String result = updateSelfStateTool.updateMood("INVALID");

        assertTrue(result.contains("Invalid mood: INVALID"));
        assertTrue(result.contains("FOCUSED, CURIOUS, ALERT"));
    }

    @Test
    @Transactional
    void updateCoreIdentity_persistsNewIdentity() {
        String newIdentity = "I am a test identity at " + System.currentTimeMillis();

        String result = updateSelfStateTool.updateCoreIdentity(newIdentity);

        assertEquals("Core identity updated.", result);
        Soul soul = Soul.findSoul();
        assertEquals(newIdentity, soul.coreIdentity);
    }

    @Test
    @Transactional
    void updateName_persistsNewName() {
        String newName = "TestAgent" + System.currentTimeMillis();

        String result = updateSelfStateTool.updateName(newName);

        assertTrue(result.contains("Name updated to: " + newName));
        Soul soul = Soul.findSoul();
        assertEquals(newName, soul.name);
    }

    @Test
    @Transactional
    void updateMood_isCaseInsensitive() {
        String result = updateSelfStateTool.updateMood("playful");

        assertTrue(result.contains("Mood updated to: PLAYFUL"));
        Soul soul = Soul.findSoul();
        assertEquals(Mood.PLAYFUL, soul.mood);
    }
}
