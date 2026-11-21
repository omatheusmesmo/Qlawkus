package dev.omatheusmesmo.qlawkus.console.skillhub;

import dev.omatheusmesmo.qlawkus.skill.SkillOrigin;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHubConfig;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillUpdates;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The install page: sources (a link or repository, pasted text, an uploaded file), the per-call and upload
 * caps, and the installed skills with their recorded origin. Reads only. Every install, update and uninstall
 * is driven from the page against the hub's authenticated {@code /api/admin/skills/install*} endpoints and the
 * existing {@code /api/admin/skills/{name}}, so the page never re-implements one.
 */
@Path("/console/skills/install")
@Authenticated
@Produces(MediaType.TEXT_HTML)
public class SkillInstallConsoleResource {

    private static final long KIB = 1024;

    public record InstalledSkill(String name, String description, String source, String installedAt,
            boolean updatable) {
    }

    private final Template template;
    private final SkillStore skillStore;
    private final SkillHubConfig config;

    public SkillInstallConsoleResource(@Location("console/skill-install.html") Template template,
            SkillStore skillStore, SkillHubConfig config) {
        this.template = template;
        this.skillStore = skillStore;
        this.config = config;
    }

    @GET
    public TemplateInstance page() {
        List<InstalledSkill> installed = skillStore.index().stream().map(this::installed).toList();
        return template.data("active", "skills")
                .data("installed", installed)
                .data("maxInstalls", config.maxInstallsPerCall())
                .data("uploadLimit", config.archive().maxBytes())
                .data("uploadLimitKib", config.archive().maxBytes() / KIB);
    }

    private InstalledSkill installed(SkillSummary summary) {
        Optional<SkillOrigin> origin = skillStore.originOf(summary.name());
        String source = origin.map(SkillOrigin::source).orElse(null);
        String installedAt = origin.map(recorded -> Instant.ofEpochMilli(recorded.installedEpochMs()).toString())
                .orElse(null);
        boolean updatable = origin.map(SkillUpdates::fetchable).orElse(false);
        return new InstalledSkill(summary.name(), summary.description(), source, installedAt, updatable);
    }
}
