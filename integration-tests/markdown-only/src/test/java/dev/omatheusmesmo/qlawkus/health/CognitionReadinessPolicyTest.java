package dev.omatheusmesmo.qlawkus.health;

import dev.omatheusmesmo.qlawkus.config.SkillsConfig;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.SoulStore;
import dev.omatheusmesmo.qlawkus.store.UserProfileStore;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import java.util.Map;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the parts of the readiness policy that a real volume cannot easily stage: what is skipped,
 * not just what is reported. Lives here rather than in {@code client/deployment} because health is
 * an optional dependency of the client, and building a response needs its provider.
 */
class CognitionReadinessPolicyTest {

    private final CognitionReadinessCheck check = new CognitionReadinessCheck();
    private final SkillsConfig skillsConfig = mock(SkillsConfig.class);

    @BeforeEach
    void wireHealthyStores() {
        check.soulStore = mock(SoulStore.class);
        check.userProfileStore = mock(UserProfileStore.class);
        check.skillStore = mock(SkillStore.class);
        check.workingMemoryStore = mock(WorkingMemoryStore.class);
        check.factStore = mock(FactStore.class);
        check.episodicStore = mock(EpisodicStore.class);
        check.skillsConfig = skillsConfig;
        when(skillsConfig.enabled()).thenReturn(true);
    }

    @Test
    void disabledSkillsAreNeitherProbedNorReported() {
        when(skillsConfig.enabled()).thenReturn(false);
        when(check.skillStore.index()).thenThrow(new IllegalStateException("broken skill root"));
        doThrow(new IllegalStateException("broken skill root")).when(check.skillStore).verifyWritable();

        HealthCheckResponse response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus(),
                "no turn reads the skill index when skills are off, so it cannot block readiness");
        assertFalse(data(response).containsKey("skills"), "nothing to report about a disabled store");
        assertFalse(data(response).containsKey("degraded"));
        verify(check.skillStore, never()).index();
        verify(check.skillStore, never()).verifyWritable();
    }

    @Test
    void anInstanceThatCannotServeSkipsTheLearningProbes() {
        doThrow(new IllegalStateException("read-only")).when(check.workingMemoryStore).verifyWritable();

        HealthCheckResponse response = check.call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        verify(check.factStore, never()).verifyWritable();
        verify(check.episodicStore, never()).verifyWritable();
        verify(check.skillStore, never()).verifyWritable();
        verify(check.soulStore, never()).verifyWritable();
        verify(check.userProfileStore, never()).verifyWritable();
    }

    private static Map<String, Object> data(HealthCheckResponse response) {
        return response.getData().orElse(Map.of());
    }
}
