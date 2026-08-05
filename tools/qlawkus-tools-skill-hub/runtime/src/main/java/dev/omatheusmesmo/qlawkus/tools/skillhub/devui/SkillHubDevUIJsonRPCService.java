package dev.omatheusmesmo.qlawkus.tools.skillhub.devui;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHub;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillRef;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs the Skill Hub card, letting a developer search the remote registry and install a skill
 * without going through the agent. Installing writes to the owned skill root, so the skill enters
 * the injected index on the next turn - the same path the {@code installSkill} tool takes.
 *
 * <p>The Dev UI deliberately bypasses the hub's {@code approval-mode} preview: that gate exists to
 * stop the model installing something unattended, and a developer clicking Install in their own dev
 * console has already given the approval the gate asks for.
 */
public class SkillHubDevUIJsonRPCService {

    @Inject
    SkillHub skillHub;

    /** Searches the configured registry and well-known hosts. */
    public List<Map<String, Object>> search(String query, int limit) {
        List<Map<String, Object>> hits = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return hits;
        }
        for (SkillRef ref : skillHub.search(query, limit <= 0 ? 10 : limit)) {
            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("name", orEmpty(ref.name()));
            hit.put("description", orEmpty(ref.description()));
            hit.put("source", orEmpty(ref.source()));
            hits.add(hit);
        }
        return hits;
    }

    /** Fetches the SKILL.md for {@code source} and saves it into the owned root. */
    public Map<String, Object> install(String source) {
        Skill installed = skillHub.install(source);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", orEmpty(installed.name()));
        result.put("description", orEmpty(installed.description()));
        return result;
    }

    private static String orEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
