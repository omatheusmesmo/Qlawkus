package dev.omatheusmesmo.qlawkus.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.config.SkillsConfig;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.OwnerChatStore;
import dev.omatheusmesmo.qlawkus.store.ScheduledTaskStore;
import dev.omatheusmesmo.qlawkus.store.SoulStore;
import dev.omatheusmesmo.qlawkus.store.UserProfileStore;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import java.util.Map;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

/**
 * Pins how readiness treats the agent-created task store: its write probe is part of the
 * learning stores, so a failing one is named under {@code degraded} and leaves the instance UP. Only
 * the turn path blocks readiness, and the task store is not on it.
 */
class CognitionReadinessCheckScheduledTasksTest {

    @Test
    void aTaskStoreThatCannotWriteIsDegradedAndTheInstanceStaysUp() {
        CognitionReadinessCheck check = healthyCheck();
        doThrow(new IllegalStateException("read-only file system")).when(check.scheduledTaskStore).verifyWritable();

        HealthCheckResponse response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("scheduledTasks", response.getData().orElseThrow().get("degraded"));
    }

    @Test
    void aWritableTaskStoreLeavesReadinessWithoutADegradedStore() {
        CognitionReadinessCheck check = healthyCheck();

        HealthCheckResponse response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertFalse(response.getData().orElse(Map.of()).containsKey("degraded"));
        verify(check.scheduledTaskStore).verifyWritable();
        verify(check.ownerChatStore).verifyWritable();
    }

    @Test
    void anOwnerChatStoreThatCannotWriteIsDegradedAndTheInstanceStaysUp() {
        CognitionReadinessCheck check = healthyCheck();
        doThrow(new IllegalStateException("read-only file system")).when(check.ownerChatStore).verifyWritable();

        HealthCheckResponse response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("ownerChats", response.getData().orElseThrow().get("degraded"));
    }

    private static CognitionReadinessCheck healthyCheck() {
        CognitionReadinessCheck check = new CognitionReadinessCheck();
        check.soulStore = mock(SoulStore.class);
        check.userProfileStore = mock(UserProfileStore.class);
        check.skillStore = mock(SkillStore.class);
        check.workingMemoryStore = mock(WorkingMemoryStore.class);
        check.factStore = mock(FactStore.class);
        check.episodicStore = mock(EpisodicStore.class);
        check.scheduledTaskStore = mock(ScheduledTaskStore.class);
        check.ownerChatStore = mock(OwnerChatStore.class);
        check.skillsConfig = mock(SkillsConfig.class);
        when(check.skillsConfig.enabled()).thenReturn(false);
        return check;
    }
}
