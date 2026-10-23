package dev.omatheusmesmo.qlawkus.it.cognition;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins how the {@code embeddings} column follows the embedding model's dimension. It used to be fixed
 * at {@code vector(1024)} by V4, which forced every provider to 1024; when NVIDIA retired its 1024
 * model and the only successor produced 2048 (#357), nothing could store a vector at all.
 *
 * <p>The column is now sized by a repeatable migration keyed on the dimension, so changing the model
 * re-applies it at the next boot. That rests on a Flyway behavior the first test proves rather than
 * assumes: a repeatable migration's checksum is taken after placeholder replacement.
 */
@QuarkusTest
class EmbeddingDimensionMigrationTest {

    private static final String SCRIPT = "db/migration/R__embedding_dimension.sql";

    @Inject
    DataSource dataSource;

    @Test
    void aRepeatableMigrationRunsAgainWhenItsPlaceholderChanges() throws SQLException {
        String schema = "flyway_probe_" + UUID.randomUUID().toString().replace("-", "");
        try {
            migrateProbe(schema, "1");
            migrateProbe(schema, "1");
            assertEquals(1, count(schema + ".probe_runs"), "an unchanged placeholder must not re-run it");

            migrateProbe(schema, "2");
            assertEquals(2, count(schema + ".probe_runs"),
                    "a changed placeholder must re-run it, or a new model dimension would never apply");
        } finally {
            execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void anUnchangedDimensionKeepsTheStoredVectors() throws Exception {
        String schema = scratchTable("[1,2,3]");
        try {
            applyScript(schema, 3);

            assertEquals(3, columnDimension(schema));
            assertEquals("[1,2,3]", firstVector(schema), "re-running at the same dimension must be a no-op");
        } finally {
            execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void aNewDimensionResizesTheColumnAndClearsVectorsTheNewModelCannotCompareWith() throws Exception {
        String schema = scratchTable("[1,2,3]");
        try {
            applyScript(schema, 4);

            assertEquals(4, columnDimension(schema));
            assertNull(firstVector(schema),
                    "a vector from the old model is in a different space, so it is cleared for re-embedding");
            assertEquals("kept text", firstText(schema), "the text survives, so the fact can be re-embedded");
            execute("INSERT INTO " + schema + ".embeddings (embedding, text) VALUES ('[1,2,3,4]', 'new')");
        } finally {
            execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private void migrateProbe(String schema, String n) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/flyway-probe")
                .placeholders(Map.of("n", n))
                .load()
                .migrate();
    }

    /** A copy of the pre-V8 shape: a fixed-size, NOT NULL vector column with one stored row. */
    private String scratchTable(String vector) throws SQLException {
        String schema = "dimension_probe_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("CREATE TABLE " + schema + ".embeddings (embedding vector(3) NOT NULL, text TEXT NOT NULL)");
        execute("INSERT INTO " + schema + ".embeddings VALUES ('" + vector + "', 'kept text')");
        return schema;
    }

    private void applyScript(String schema, int newDimension) throws SQLException, IOException {
        String sql;
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(SCRIPT)) {
            assertTrue(in != null, SCRIPT + " is missing");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${embedding_dimension}", Integer.toString(newDimension));
        }
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema + ", public");
            try {
                statement.execute(sql);
            } finally {
                statement.execute("RESET search_path");
            }
        }
    }

    private int columnDimension(String schema) throws SQLException {
        return queryInt("SELECT atttypmod FROM pg_attribute WHERE attrelid = '" + schema
                + ".embeddings'::regclass AND attname = 'embedding'");
    }

    private String firstVector(String schema) throws SQLException {
        return queryString("SELECT embedding::text FROM " + schema + ".embeddings LIMIT 1");
    }

    private String firstText(String schema) throws SQLException {
        return queryString("SELECT text FROM " + schema + ".embeddings LIMIT 1");
    }

    private long count(String table) throws SQLException {
        return queryInt("SELECT count(*) FROM " + table);
    }

    private int queryInt(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private String queryString(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
