package dev.omatheusmesmo.qlawkus.tools.skillhub;

import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.NameSource;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Authenticated install endpoints for the console (and any admin client): a link or slug, a listing of
 * a repository folder, archive or gist, a selection from that listing, pasted SKILL.md text, an uploaded
 * SKILL.md or archive (listed, then a selection), and an update of an installed skill from its source.
 *
 * <p>The REST call is the approval. The owner is authenticated and posts the install directly, so the
 * HITL preview round-trip the agent's tools use does not apply here. The same limits do: the per-call
 * cap, the SKILL.md size cap, the archive caps, the outbound policy and the same name validation.
 *
 * <p>Failures return a JSON {@code error} with the reason and a {@code kind}: {@code refused} (400 for input
 * the caller must fix, or 422 when the source was read but holds nothing installable), {@code limit} (400,
 * a size or count cap), {@code unreachable} (422, the source could not be reached), or {@code disabled}
 * (409, the hub is switched off).
 */
@Path("/api/admin/skills/install")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class SkillInstallResource {

  private static final String DISABLED = "The skill hub is disabled (qlawkus.skill-hub.enabled=false).";
  private static final int BAD_REQUEST = 400;
  private static final int CONFLICT = 409;
  private static final int UNPROCESSABLE = 422;
  private static final String REFUSED = "refused";
  private static final String LIMIT = "limit";
  private static final String UNREACHABLE = "unreachable";
  private static final String DISABLED_KIND = "disabled";

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
      return ok(Map.of("installed", List.of(installed.name())));
    });
  }

  @POST
  @Path("/list")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response list(SourceRequest request) {
    return attempt(() -> {
      String source = requireSource(request == null ? null : request.source());
      return ok(Map.of("skills", entries(skillHub.discover(source))));
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
      return ok(Map.of("installed", SkillSelection.installAll(skillHub, selected)));
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
      return ok(Map.of("installed", List.of(skill.name())));
    });
  }

  @POST
  @Path("/upload/list")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response uploadList(@RestForm("file") FileUpload file) {
    return attempt(() -> ok(Map.of("skills", entries(discoverUpload(file)))));
  }

  @POST
  @Path("/upload/selection")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response uploadSelection(@RestForm("file") FileUpload file, @RestForm("skills") String skills) {
    return attempt(() -> {
      String fileName = uploadName(file);
      List<SkillCandidate> selected = SkillSelection.select(discoverUpload(file),
          skills == null ? "" : skills, fileName);
      SkillSelection.requireWithinCap(selected.size(), config.maxInstallsPerCall());
      return ok(Map.of("installed", SkillSelection.installAll(skillHub, selected)));
    });
  }

  @POST
  @Path("/update/{name}")
  public Response update(@PathParam("name") String name) {
    return attempt(() -> {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Name the installed skill to update.");
      }
      SkillUpdates.refresh(skillHub, skillStore, name.strip());
      return Response.noContent().build();
    });
  }

  private List<SkillCandidate> discoverUpload(FileUpload file) {
    String fileName = uploadName(file);
    try {
      return skillHub.discoverUpload(fileName, Files.readAllBytes(file.filePath()));
    } catch (IOException e) {
      throw new IllegalStateException("Could not read the upload '" + fileName + "'.", e);
    }
  }

  private static String uploadName(FileUpload file) {
    if (file == null) {
      throw new IllegalArgumentException("Choose a SKILL.md or an archive (.zip, .tar.gz) to upload.");
    }
    String name = file.fileName() == null ? "" : file.fileName();
    String base = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).strip();
    return base.isEmpty() ? "upload" : base;
  }

  private static List<Map<String, Object>> entries(List<SkillCandidate> candidates) {
    return candidates.stream()
        .map(candidate -> {
          Map<String, Object> entry = new LinkedHashMap<>();
          entry.put("name", candidate.name());
          entry.put("location", candidate.location() == null ? "" : candidate.location());
          return entry;
        })
        .toList();
  }

  private String requireSource(String source) {
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException("Provide the skill source: an owner/repo slug or a link.");
    }
    return source.strip();
  }

  private Response attempt(Supplier<Response> action) {
    if (!config.enabled()) {
      return failure(CONFLICT, DISABLED_KIND, DISABLED);
    }
    try {
      return action.get();
    } catch (SkillLimitException e) {
      return failure(BAD_REQUEST, LIMIT, e.getMessage());
    } catch (IllegalArgumentException e) {
      return failure(BAD_REQUEST, REFUSED, e.getMessage());
    } catch (RuntimeException e) {
      return failure(UNPROCESSABLE, isUnreachable(e) ? UNREACHABLE : REFUSED, e.getMessage());
    }
  }

  private static boolean isUnreachable(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof TransientHttpException || cause instanceof IOException) {
        return true;
      }
    }
    return false;
  }

  private static Response ok(Object body) {
    return Response.ok(body).build();
  }

  private static Response failure(int status, String kind, String message) {
    return Response.status(status)
        .entity(Map.of("error", String.valueOf(message), "kind", kind))
        .build();
  }
}
