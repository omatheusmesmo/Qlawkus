package dev.omatheusmesmo.qlawkus.audit;

import tools.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class PgDebugJournal {
    private static final int QUEUE_SIZE = 2048;
    private static final int BATCH_SIZE = 256;
    private static final String COLUMNS = """
            occurred_at, kind, run_id, invocation_id, trace_id, task_id, conversation_id,
            subject, call_id, outcome, detail, excerpt
            """;
    private static final String INSERT = "INSERT INTO debug_event (" + COLUMNS
            + ") VALUES (?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?)";

    @Inject AgroalDataSource dataSource;
    @Inject ObjectMapper json;
    @Inject DebugHistoryConfig config;

    private final ArrayBlockingQueue<DebugEntry> pending = new ArrayBlockingQueue<>(QUEUE_SIZE);
    private final AtomicLong dropped = new AtomicLong();

    void enqueue(DebugEntry entry) {
        if (config.enabled() && !pending.offer(entry)) {
            dropped.incrementAndGet();
        }
    }

    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void flush() {
        if (pending.isEmpty()) return;
        List<DebugEntry> batch = new ArrayList<>(BATCH_SIZE);
        pending.drainTo(batch, BATCH_SIZE);
        if (!batch.isEmpty()) {
            try {
                append(batch);
            } catch (SQLException error) {
                dropped.addAndGet(batch.size());
                Log.warnf("Debug history write failed (SQL state %s)", error.getSQLState());
            }
        }
    }

    private void append(List<DebugEntry> batch) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var stmt = connection.prepareStatement(INSERT)) {
                for (DebugEntry e : batch) {
                    stmt.setObject(1, utc(e.occurredAt()));
                    stmt.setString(2, e.kind());
                    stmt.setObject(3, e.runId());
                    stmt.setObject(4, e.invocationId());
                    stmt.setString(5, e.traceId());
                    stmt.setString(6, e.taskId());
                    stmt.setString(7, e.conversationId());
                    stmt.setString(8, e.subject());
                    stmt.setString(9, e.callId());
                    stmt.setString(10, e.outcome());
                    stmt.setString(11, e.detail().toString());
                    stmt.setString(12, e.excerpt());
                    stmt.addBatch();
                }
                stmt.executeBatch();
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    @Scheduled(every = "1h", delayed = "60s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void expire() {
        pruneAt(Instant.now());
    }

    void pruneAt(Instant now) {
        int days = Math.clamp(config.retentionDays(), 1, 90);
        try (var connection = dataSource.getConnection();
             var excerpts = connection.prepareStatement(
                     "UPDATE debug_event SET excerpt=NULL WHERE excerpt IS NOT NULL AND occurred_at < ?");
             var events = connection.prepareStatement("DELETE FROM debug_event WHERE occurred_at < ?")) {
            excerpts.setObject(1, utc(now.minus(1, ChronoUnit.DAYS)));
            excerpts.executeUpdate();
            events.setObject(1, utc(now.minus(days, ChronoUnit.DAYS)));
            events.executeUpdate();
        } catch (SQLException error) {
            Log.warnf("Debug history retention failed (SQL state %s)", error.getSQLState());
        }
    }

    public record Filter(String taskId, UUID runId, UUID invocationId, String traceId) {}

    public List<DebugEntry> recent(Filter filter, int limit, boolean showText) {
        String sql = "SELECT id, " + COLUMNS + " FROM debug_event WHERE true";
        List<Object> parameters = new ArrayList<>();
        if (filter.taskId() != null) {
            sql += " AND task_id = ?";
            parameters.add(filter.taskId());
        }
        if (filter.runId() != null) {
            sql += " AND run_id = ?";
            parameters.add(filter.runId());
        }
        if (filter.invocationId() != null) {
            sql += " AND invocation_id = ?";
            parameters.add(filter.invocationId());
        }
        if (filter.traceId() != null) {
            sql += " AND trace_id = ?";
            parameters.add(filter.traceId());
        }
        sql += " ORDER BY occurred_at DESC, id DESC LIMIT ?";
        parameters.add(Math.clamp(limit, 1, 250));

        try (var connection = dataSource.getConnection(); var stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }
            try (var rows = stmt.executeQuery()) {
                List<DebugEntry> result = new ArrayList<>();
                Instant excerptCutoff = Instant.now().minus(1, ChronoUnit.DAYS);
                while (rows.next()) {
                    Instant at = rows.getObject("occurred_at", OffsetDateTime.class).toInstant();
                    result.add(new DebugEntry(rows.getLong("id"), at, rows.getString("kind"),
                            rows.getObject("run_id", UUID.class), rows.getObject("invocation_id", UUID.class),
                            rows.getString("trace_id"), rows.getString("task_id"), rows.getString("conversation_id"),
                            rows.getString("subject"), rows.getString("call_id"), rows.getString("outcome"),
                            json.readTree(rows.getString("detail")),
                            showText && config.captureText() && at.isAfter(excerptCutoff) ? rows.getString("excerpt") : null));
                }
                return List.copyOf(result);
            }
        } catch (SQLException | tools.jackson.core.JacksonException error) {
            throw new IllegalStateException("Could not read the debug history");
        }
    }

    public long dropped() {
        return dropped.get();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
