package dev.omatheusmesmo.qlawkus.tools.brag;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.agent.Logged;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

@QlawTool
@ApplicationScoped
@Logged
public class BragTool {

    @Inject
    BragConfig config;

    @Inject
    ImpactTranslator impactTranslator;

    @Tool("Record a career achievement or accomplishment. Provide a description of what was done, optionally the date and the repository it relates to.")
    @Transactional
    public String addAchievement(
            @P("Description of the achievement or accomplishment") String achievement,
            @P(value = "Date of the achievement in ISO-8601 format, e.g. 2026-05-07. Defaults to today.", required = false) String date,
            @P(value = "Repository name this achievement relates to, e.g. my-project", required = false) String repo) {
        LocalDate entryDate = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now(ZoneOffset.UTC);

        BragEntry existing = BragEntry.findDuplicate(entryDate, achievement, repo);
        if (existing != null) {
            return "Duplicate achievement already recorded for " + entryDate + ".";
        }

        BragEntry entry = new BragEntry();
        entry.date = entryDate;
        entry.achievement = achievement;
        entry.repo = repo;
        entry.deleted = false;

        if (config.impactTranslationEnabled()) {
            try {
                entry.impact = impactTranslator.translate(achievement);
            } catch (Exception e) {
                Log.warnf(e, "Impact translation failed, storing achievement without impact");
                entry.impact = null;
            }
        }

        entry.persist();
        String result = "Achievement recorded for " + entryDate + ".";
        if (entry.impact != null) {
            result += " Business impact: " + entry.impact;
        }
        return result;
    }

    @Tool("Generate a Markdown brag document summarizing recorded achievements within a date range. Useful for performance reviews, status updates, or self-reflection.")
    public String generateMarkdownReport(
            @P(value = "Start date in ISO-8601 format, e.g. 2026-01-01. Defaults to 30 days ago.", required = false) String startDate,
            @P(value = "End date in ISO-8601 format, e.g. 2026-05-07. Defaults to today.", required = false) String endDate) {
        LocalDate from = (startDate != null && !startDate.isBlank())
                ? LocalDate.parse(startDate)
                : LocalDate.now(ZoneOffset.UTC).minusDays(30);
        LocalDate to = (endDate != null && !endDate.isBlank())
                ? LocalDate.parse(endDate)
                : LocalDate.now(ZoneOffset.UTC);

        var entries = BragEntry.findActiveByDateRange(from, to);

        if (entries.isEmpty()) {
            return "No achievements found between " + from + " and " + to + ".";
        }

        StringBuilder markdown = new StringBuilder();
        markdown.append("# Brag Document\n\n");
        markdown.append("Period: ").append(from).append(" to ").append(to).append("\n\n");
        markdown.append("## Achievements\n\n");

        for (BragEntry entry : entries) {
            markdown.append("- **").append(entry.date).append("** — ").append(entry.achievement);
            if (entry.impact != null && !entry.impact.isBlank()) {
                markdown.append(" _(Impact: ").append(entry.impact).append(")_");
            }
            if (entry.repo != null && !entry.repo.isBlank()) {
                markdown.append(" [").append(entry.repo).append("]");
            }
            markdown.append("\n");
        }

        markdown.append("\n---\n_Total: ").append(entries.size()).append(" achievements._\n");
        return markdown.toString();
    }

    @Tool("Soft-delete a recorded achievement by its ID. The entry will be marked as deleted and excluded from reports.")
    @Transactional
    public String deleteAchievement(@P("ID of the achievement to delete") long id) {
        BragEntry entry = BragEntry.find("id = ?1", id).firstResult();
        if (entry == null) {
            return "Achievement with ID " + id + " not found.";
        }
        if (entry.deleted) {
            return "Achievement with ID " + id + " is already deleted.";
        }
        entry.deleted = true;
        return "Achievement " + id + " marked as deleted.";
    }
}
