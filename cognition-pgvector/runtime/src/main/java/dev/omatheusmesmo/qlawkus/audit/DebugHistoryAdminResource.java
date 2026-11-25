package dev.omatheusmesmo.qlawkus.audit;

import dev.omatheusmesmo.qlawkus.store.pg.CognitionBackendState;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestResponse.Status;

@Authenticated
@Path("/api/admin/debug-history")
@Produces(MediaType.APPLICATION_JSON)
public class DebugHistoryAdminResource {
    public record History(List<DebugEntry> events, long droppedSinceBoot) {}

    private final PgDebugJournal journal;
    private final CognitionBackendState backend;

    public DebugHistoryAdminResource(PgDebugJournal journal, CognitionBackendState backend) {
        this.journal = journal;
        this.backend = backend;
    }

    @GET
    public RestResponse<?> read(@QueryParam("taskId") String taskId, @QueryParam("runId") UUID runId,
                        @QueryParam("invocationId") UUID invocationId, @QueryParam("traceId") String traceId,
                        @QueryParam("limit") @DefaultValue("100") int limit,
                        @QueryParam("includeText") @DefaultValue("false") boolean includeText) {
        if (!backend.usesPostgres()) {
            return RestResponse.ResponseBuilder.create(Status.CONFLICT, Map.of("error",
                    "this boot runs on markdown, which keeps no debug history; set qlawkus.cognition.backend to hybrid or pgvector and configure a datasource")).build();
        }
        var history = new History(journal.recent(new PgDebugJournal.Filter(taskId, runId, invocationId, traceId),
                limit, includeText), journal.dropped());
        return RestResponse.ResponseBuilder.ok(history).header("Cache-Control", "no-store").build();
    }
}
