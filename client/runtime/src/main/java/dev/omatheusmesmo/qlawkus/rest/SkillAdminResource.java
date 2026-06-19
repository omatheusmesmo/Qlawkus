package dev.omatheusmesmo.qlawkus.rest;

import dev.omatheusmesmo.qlawkus.cognition.SkillAdminService;
import dev.omatheusmesmo.qlawkus.cognition.SkillCurationJob;
import dev.omatheusmesmo.qlawkus.cognition.SkillLifecycleJob;
import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/api/admin/skills")
@Authenticated
public class SkillAdminResource {

  @Inject
  SkillAdminService adminService;

  @Inject
  SkillCurationJob curationJob;

  @Inject
  SkillLifecycleJob lifecycleJob;

  @GET
  public List<SkillSummary> list() {
    return adminService.listSkills();
  }

  @GET
  @Path("/{name}")
  public Response get(@PathParam("name") String name) {
    return adminService.getSkill(name)
        .map(skill -> Response.ok(skill).build())
        .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
  }

  @DELETE
  @Path("/{name}")
  public Response delete(@PathParam("name") String name) {
    return adminService.deleteSkill(name)
        ? Response.noContent().build()
        : Response.status(Response.Status.NOT_FOUND).build();
  }

  @POST
  @Path("/curate")
  public Response curate() {
    long removed = curationJob.curateNow();
    return Response.ok(Map.of("removed", removed)).build();
  }

  @POST
  @Path("/lifecycle")
  public Response lifecycle() {
    return Response.ok(Map.of("transitioned", lifecycleJob.sweepNow())).build();
  }

  @POST
  @Path("/{name}/pin")
  public Response pin(@PathParam("name") String name,
      @QueryParam("pinned") @DefaultValue("true") boolean pinned) {
    return adminService.setPinned(name, pinned)
        ? Response.ok(Map.of("name", name, "pinned", pinned)).build()
        : Response.status(Response.Status.NOT_FOUND).build();
  }
}
