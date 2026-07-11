package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * The skills management page. Lists the agent's skills from the client-side {@link SkillStore}; the
 * per-skill (pin, delete) and maintenance (curate, lifecycle) actions are driven from the page by
 * HTMX against the existing {@code /api/admin/skills} endpoints.
 */
@Path("/console/skills")
@Authenticated
@Produces(MediaType.TEXT_HTML)
public class SkillsConsoleResource {

    private final Template skills;
    private final SkillStore skillStore;

    public SkillsConsoleResource(@Location("console/skills.html") Template skills, SkillStore skillStore) {
        this.skills = skills;
        this.skillStore = skillStore;
    }

    @GET
    public TemplateInstance page() {
        List<SkillSummary> index = skillStore.index();
        return skills
                .data("active", "skills")
                .data("skills", index)
                .data("skillCount", index.size());
    }
}
