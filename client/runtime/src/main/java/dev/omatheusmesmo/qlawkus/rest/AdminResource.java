package dev.omatheusmesmo.qlawkus.rest;

import dev.omatheusmesmo.qlawkus.cognition.MemoryAdminService;
import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import dev.omatheusmesmo.qlawkus.dto.MemorySummary;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/api/admin/memory")
@Authenticated
public class AdminResource {

  @Inject
  MemoryAdminService adminService;

  @GET
  public MemorySummary list() {
    return adminService.getMemorySummary();
  }

  @GET
  @Path("/journals")
  public List<JournalSummary> listJournals() {
    return adminService.listJournals();
  }

  @DELETE
  public Response purge(
      @QueryParam("source") String source,
      @QueryParam("includeJournals") Boolean includeJournals,
      @QueryParam("all") Boolean purgeAll
  ) {
    if (purgeAll != null && purgeAll) {
      adminService.purgeAllMemory();
      return Response.noContent().build();
    }

    if (source != null) {
      long deleted = adminService.purgeEmbeddingsBySource(source);
      return Response.ok(Map.of("deleted", deleted)).build();
    }

    if (includeJournals != null && includeJournals) {
      long deleted = adminService.purgeJournals();
      return Response.ok(Map.of("deleted", deleted)).build();
    }

    return Response.status(Response.Status.BAD_REQUEST)
        .entity(Map.of("error", "Specify source, includeJournals=true, or all=true"))
        .build();
  }
}
