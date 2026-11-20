package dev.omatheusmesmo.qlawkus.tools.skillhub;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.NameSource;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Authenticated install endpoints for the console (and any admin client): a link or slug, a listing of
 * a repository folder, archive or gist, a selection from that listing, and pasted SKILL.md text.
 *
 * <p>The REST call is the approval. The owner is authenticated and posts the install directly, so the
 * HITL preview round-trip the agent's tools use does not apply here. The same limits do: the per-call
 * cap, the SKILL.md size cap, the archive caps, the outbound policy and the same name validation.
 * Failures return a JSON {@code error} with the reason: 400 for input the caller must fix, 422 when the
 * source could not be read or installed.
 */
@Path("/api/admin/skills/install")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class SkillInstallResource {

  private static final String DISABLED = "The skill hub is disabled (qlawkus.skill-hub.enabled=false).";
  private static final int UNPROCESSABLE = 422;

  /** A source to install or list: an owner/repo slug, a skill link, a folder, an archive or a gist. */
  public record SourceRequest(String source) {
  }

  /** A selection from the listing of {@code source}: {@code "all"} or comma-separated skill names. */
  public record SelectionRequest(String source, String skills) {
  }

  private final SkillHub skillHub;
  private final SkillStore skillStore;
  private final SkillHubConfig config;

  @Inject
  public SkillInstallResource(SkillHub skillHub, SkillStore skillStore, SkillHubConfig config) {
    this.skillHub = skillHub;
    this.skillStore = skillStore;
    this.config = config;
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response install(SourceRequest request) {
    return attempt(() -> {
      String source = requireSource(request == null ? null : request.source());
      if (SkillSourceParser.parse(source) instanceof NameSource) {
        throw new IllegalArgumentException("'" + source + "' is a skill name. Paste a link or an owner/repo "
            + "slug; search the registry from the agent's searchSkillHub tool.");
      }
      Skill installed = skillHub.install(source);
      return Map.of("installed", List.of(installed.name()));
    });
  }

  @POST
  @Path("/list")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response list(SourceRequest request) {
    return attempt(() -> {
      String source = requireSource(request == null ? null : request.source());
      List<Map<String, Object>> skills = skillHub.discover(source).stream()
          .map(candidate -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", candidate.name());
            entry.put("location", candidate.location() == null ? "" : candidate.location());
            return entry;
          })
          .toList();
      return Map.of("skills", skills);
    });
  }

  @POST
  @Path("/selection")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response selection(SelectionRequest request) {
    return attempt(() -> {
      String source = requireSource(request == null ? null : request.source());
      String skills = request.skills() == null ? "" : request.skills();
      List<SkillCandidate> selected = SkillSelection.select(skillHub.discover(source), skills, source);
      SkillSelection.requireWithinCap(selected.size(), config.maxInstallsPerCall());
      return Map.of("installed", SkillSelection.installAll(skillHub, selected));
    });
  }

  @POST
  @Path("/text")
  @Consumes({MediaType.TEXT_PLAIN, "text/markdown"})
  public Response text(String text) {
    return attempt(() -> {
      if (text == null || text.isBlank()) {
        throw new IllegalArgumentException("Paste the SKILL.md text to install.");
      }
      Skill skill = skillStore.save(SkillMarkdown.parsePasted(text, config.maxSkillBytes()));
      return Map.of("installed", List.of(skill.name()));
    });
  }

  private String requireSource(String source) {
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException("Provide the skill source: an owner/repo slug or a link.");
    }
    return source.strip();
  }

  private Response attempt(Supplier<Object> action) {
    if (!config.enabled()) {
      return Response.status(Response.Status.CONFLICT.getStatusCode()).entity(Map.of("error", DISABLED)).build();
    }
    try {
      return Response.ok(action.get()).build();
    } catch (IllegalArgumentException e) {
      return failure(Response.Status.BAD_REQUEST.getStatusCode(), e);
    } catch (RuntimeException e) {
      return failure(UNPROCESSABLE, e);
    }
  }

  private static Response failure(int status, RuntimeException e) {
    return Response.status(status).entity(Map.of("error", String.valueOf(e.getMessage()))).build();
  }
}
