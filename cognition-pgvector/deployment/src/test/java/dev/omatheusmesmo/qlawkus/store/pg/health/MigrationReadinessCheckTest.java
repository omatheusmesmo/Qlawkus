package dev.omatheusmesmo.qlawkus.store.pg.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.store.pg.CognitionBackendState;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.Test;

/**
 * A readiness poll must not run Flyway's {@code info()} once the schema is current: Flyway logs its
 * {@code Database:} line at INFO on every call, which is what flooded the console at one line per
 * poll. A pending migration keeps the check live, so DOWN still follows the schema. On a markdown boot
 * the Flyway bean is inactive and asking it throws, so the check reports UP without touching it.
 */
class MigrationReadinessCheckTest {

    @Test
    void settledSchemaIsNotReQueriedOnEveryPoll() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService infos = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(infos);
        when(infos.pending()).thenReturn(new MigrationInfo[0]);
        MigrationReadinessCheck check = check(flyway);

        for (int poll = 0; poll < 5; poll++) {
            assertEquals(HealthCheckResponse.Status.UP, check.call().getStatus());
        }

        verify(flyway, times(1)).info();
    }

    @Test
    void pendingMigrationKeepsTheCheckDownAndLive() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService infos = mock(MigrationInfoService.class);
        MigrationInfo next = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(infos);
        when(infos.pending()).thenReturn(new MigrationInfo[] {next});
        when(next.getDescription()).thenReturn("add column");
        MigrationReadinessCheck check = check(flyway);

        assertEquals(HealthCheckResponse.Status.DOWN, check.call().getStatus());
        assertEquals(HealthCheckResponse.Status.DOWN, check.call().getStatus());

        verify(flyway, times(2)).info();
    }

    @Test
    void markdownBootIsReadyWithoutAskingFlyway() {
        Flyway flyway = mock(Flyway.class);
        MigrationReadinessCheck check = check(flyway, false);

        assertEquals(HealthCheckResponse.Status.UP, check.call().getStatus());

        verify(flyway, never()).info();
    }

    private static MigrationReadinessCheck check(Flyway flyway) {
        return check(flyway, true);
    }

    @SuppressWarnings("unchecked")
    private static MigrationReadinessCheck check(Flyway flyway, boolean usesPostgres) {
        Instance<Flyway> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(true);
        when(instance.get()).thenReturn(flyway);
        MigrationReadinessCheck check = new MigrationReadinessCheck();
        check.flyway = instance;
        CognitionBackendState backend = mock(CognitionBackendState.class);
        when(backend.usesPostgres()).thenReturn(usesPostgres);
        check.backend = backend;
        return check;
    }
}
