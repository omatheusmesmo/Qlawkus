package dev.omatheusmesmo.qlawkus.audit;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;
import org.jboss.resteasy.reactive.RestResponse;

@Authenticated
@Path("/api/admin/debug-history")
@Produces(MediaType.APPLICATION_JSON)
public class DebugHistoryAdminResource {
    public record History(List<DebugEntry> events, long droppedSinceBoot) {}

    private final PgDebugJournal journal;

    public DebugHistoryAdminResource(PgDebugJournal journal) {
        this.journal = journal;
    }

    @GET
    public RestResponse<History> read(@QueryParam("taskId") String taskId, @QueryParam("runId") UUID runId,
                        @QueryParam("invocationId") UUID invocationId, @QueryParam("traceId") String traceId,
                        @QueryParam("limit") @DefaultValue("100") int limit,
                        @QueryParam("includeText") @DefaultValue("false") boolean includeText) {
        var history = new History(journal.recent(new PgDebugJournal.Filter(taskId, runId, invocationId, traceId),
                limit, includeText), journal.dropped());
        return RestResponse.ResponseBuilder.ok(history).header("Cache-Control", "no-store").build();
    }
}
