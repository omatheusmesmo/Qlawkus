package dev.omatheusmesmo.qlawkus.store.pg.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.Test;

/**
 * A readiness poll must not run Flyway's {@code info()} once the schema is current: Flyway logs its
 * {@code Database:} line at INFO on every call, which is what flooded the console at one line per
 * poll. A pending migration keeps the check live, so DOWN still follows the schema.
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

    @SuppressWarnings("unchecked")
    private static MigrationReadinessCheck check(Flyway flyway) {
        Instance<Flyway> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(true);
        when(instance.get()).thenReturn(flyway);
        MigrationReadinessCheck check = new MigrationReadinessCheck();
        check.flyway = instance;
        return check;
    }
}
