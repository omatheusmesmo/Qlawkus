package dev.omatheusmesmo.qlawkus.store.pg.reconcile;

import dev.omatheusmesmo.qlawkus.store.pg.reconcile.CognitionReconciler.Direction;
import dev.omatheusmesmo.qlawkus.store.pg.reconcile.CognitionReconciler.Stats;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/**
 * Admin endpoints for reconciling/migrating cognition data between the markdown files and pgvector.
 * Only present when the qlawkus-cognition-pgvector extension is on the classpath.
 */
@Path("/api/admin/cognition")
@Authenticated
public class CognitionReconcileResource {

  @Inject
  CognitionReconciler reconciler;

  @Inject
  CognitionMigrator migrator;

  /** Bidirectional, non-destructive union of files and pgvector. Idempotent. */
  @POST
  @Path("/reconcile")
  public Stats reconcile() {
    return reconciler.reconcileAll();
  }

  /**
   * One-directional copy, overwriting destination singletons.
   * {@code direction=files-to-pg} or {@code direction=pg-to-files}.
   */
  @POST
  @Path("/migrate")
  public Response migrate(@QueryParam("direction") String direction) {
    Direction parsed = parse(direction);
    if (parsed == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "direction must be 'files-to-pg' or 'pg-to-files'"))
          .build();
    }
    return Response.ok(migrator.migrate(parsed)).build();
  }

  private static Direction parse(String direction) {
    if (direction == null) {
      return null;
    }
    return switch (direction.toLowerCase().replace('_', '-')) {
      case "files-to-pg" -> Direction.FILES_TO_PG;
      case "pg-to-files" -> Direction.PG_TO_FILES;
      default -> null;
    };
  }
}
