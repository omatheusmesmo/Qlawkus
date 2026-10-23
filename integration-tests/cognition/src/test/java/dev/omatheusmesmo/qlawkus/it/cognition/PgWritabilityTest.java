package dev.omatheusmesmo.qlawkus.it.cognition;

import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.SoulStore;
import dev.omatheusmesmo.qlawkus.store.UserProfileStore;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import dev.omatheusmesmo.qlawkus.store.pg.PgWriteProbe;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Postgres half of the readiness write probe against a real database. The case it exists
 * for is a session that can read but not write - a hot standby, or a database put into
 * {@code default_transaction_read_only} - which every {@code load()} passes and the first
 * {@code remember} then fails on.
 */
@QuarkusTest
class PgWritabilityTest {

    @Inject
    DataSource dataSource;

    @Inject
    SoulStore soul;

    @Inject
    UserProfileStore owner;

    @Inject
    FactStore facts;

    @Inject
    EpisodicStore episodic;

    @Inject
    WorkingMemoryStore workingMemory;

    @Inject
    SkillStore skills;

    @Test
    void everyStoreProbesATableThatExists() {
        Map<String, Runnable> probes = Map.of(
                "soul", soul::verifyWritable,
                "owner", owner::verifyWritable,
                "facts", facts::verifyWritable,
                "episodic", episodic::verifyWritable,
                "workingMemory", workingMemory::verifyWritable,
                "skills", skills::verifyWritable);

        probes.forEach((store, probe) -> assertDoesNotThrow(probe::run,
                store + " probed a table this schema does not have, or could not write to it"));
    }

    @Test
    void aReadOnlySessionIsRefused() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY");
            try {
                IllegalStateException refused = assertThrows(IllegalStateException.class,
                        () -> PgWriteProbe.verify(connection, PgWriteProbe.Table.CHAT_MESSAGES),
                        "a read-only session reads fine, so only a write probe can catch it");
                assertTrue(refused.getMessage().contains("chat_message_entity"),
                        "the failure must name the table: " + refused.getMessage());
            } finally {
                statement.execute("SET SESSION CHARACTERISTICS AS TRANSACTION READ WRITE");
            }
        }
    }

    @Test
    void aLeastPrivilegeRoleKeepsWorkingMemoryAndJournalsWritable() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            asLeastPrivilegeRole(statement, () -> {
                assertDoesNotThrow(() -> PgWriteProbe.verify(connection, PgWriteProbe.Table.CHAT_MESSAGES),
                        "working memory only inserts and deletes, so it must not demand UPDATE");
                assertDoesNotThrow(() -> PgWriteProbe.verify(connection, PgWriteProbe.Table.JOURNAL),
                        "journals only insert and delete, so they must not demand UPDATE");
            });
        }
    }

    @Test
    void aRoleWithoutTheDmlAStoreIssuesIsRefused() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            asLeastPrivilegeRole(statement, () -> {
                IllegalStateException refused = assertThrows(IllegalStateException.class,
                        () -> PgWriteProbe.verify(connection, PgWriteProbe.Table.SOUL));
                assertTrue(refused.getMessage().contains("no UPDATE privilege"),
                        "the persona is saved by UPDATE, which this role lacks: " + refused.getMessage());
            });
        }
    }

    @Test
    void probingWritesNothing() throws SQLException {
        long before = rows("chat_message_entity");

        workingMemory.verifyWritable();
        workingMemory.verifyWritable();

        assertEquals(before, rows("chat_message_entity"),
                "readiness polls every few seconds, so a probe that inserts would fill the table");
    }

    /**
     * Runs {@code body} as a role granted exactly what a DBA applying least privilege would grant the
     * working-memory and journal tables, and only SELECT on the persona.
     */
    private static void asLeastPrivilegeRole(Statement statement, Runnable body) throws SQLException {
        String role = "qlawkus_probe_" + UUID.randomUUID().toString().replace("-", "");
        statement.execute("CREATE ROLE " + role + " NOLOGIN");
        try {
            statement.execute("GRANT SELECT, INSERT, DELETE ON chat_message_entity, journal TO " + role);
            statement.execute("GRANT SELECT ON soul TO " + role);
            statement.execute("SET ROLE " + role);
            try {
                body.run();
            } finally {
                statement.execute("RESET ROLE");
            }
        } finally {
            statement.execute("DROP OWNED BY " + role);
            statement.execute("DROP ROLE " + role);
        }
    }

    private long rows(String table) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT count(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }
}
