package dev.omatheusmesmo.qlawkus.devui;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.omatheusmesmo.qlawkus.cognition.EpisodicConsolidatorJob;
import dev.omatheusmesmo.qlawkus.cognition.MemoryAdminService;
import dev.omatheusmesmo.qlawkus.cognition.MemoryCurationJob;
import dev.omatheusmesmo.qlawkus.cognition.MemoryReviewJob;
import dev.omatheusmesmo.qlawkus.cognition.Mood;
import dev.omatheusmesmo.qlawkus.cognition.SkillAdminService;
import dev.omatheusmesmo.qlawkus.cognition.SkillCurationJob;
import dev.omatheusmesmo.qlawkus.cognition.SkillLifecycleJob;
import dev.omatheusmesmo.qlawkus.cognition.Soul;
import dev.omatheusmesmo.qlawkus.cognition.UserProfile;
import dev.omatheusmesmo.qlawkus.composition.CompositionManifest;
import dev.omatheusmesmo.qlawkus.composition.CompositionManifestParser;
import dev.omatheusmesmo.qlawkus.dto.MemorySummary;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.MemorySource;
import dev.omatheusmesmo.qlawkus.store.SoulStore;
import dev.omatheusmesmo.qlawkus.store.UserProfileStore;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.arc.ClientProxy;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs the Dev UI pages, reading and writing the live agent state through the same SPIs the agent
 * itself uses, so a page can never show or change something the running agent does not see.
 * Registered as a bean only by the Dev UI build step, which runs under local dev mode; a production
 * build never wires it.
 *
 * <p>Mutating methods exist because the point of the card is to let a developer shape the agent
 * while it runs. Job triggers delegate to the jobs' own manual-run methods - the same entry points
 * the {@code /api/admin/*} endpoints call - rather than reimplementing them.
 */
public class QlawkusDevUIJsonRPCService {

    @Inject
    SoulStore soulStore;

    @Inject
    UserProfileStore userProfileStore;

    @Inject
    FactStore factStore;

    @Inject
    MemoryAdminService memoryAdminService;

    @Inject
    SkillAdminService skillAdminService;

    @Inject
    MemoryReviewJob memoryReviewJob;

    @Inject
    MemoryCurationJob memoryCurationJob;

    @Inject
    EpisodicConsolidatorJob episodicConsolidatorJob;

    @Inject
    SkillCurationJob skillCurationJob;

    @Inject
    SkillLifecycleJob skillLifecycleJob;

    @Inject
    DevUiCompositionSource compositionSource;

    @Inject
    @QlawTool
    Instance<Object> registeredTools;

    // ---------------------------------------------------------------- persona

    /**
     * The persona injected into every system message. Absent values are emitted as empty strings
     * rather than nulls, because the JSON-RPC serializer drops null entries and the pages rely on
     * every key being present.
     */
    public Map<String, Object> getSoul() {
        Soul soul = soulStore.load();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", orEmpty(soul.name));
        result.put("coreIdentity", orEmpty(soul.coreIdentity));
        result.put("currentState", orEmpty(soul.currentState));
        result.put("mood", soul.mood == null ? "" : soul.mood.name());
        result.put("updatedAt", orEmpty(soul.updatedAt));
        result.put("moods", Arrays.stream(Mood.values()).map(Enum::name).toList());
        return result;
    }

    /** Rewrites the persona. Takes effect on the next turn, since SoulEngine reads it each time. */
    public Map<String, Object> saveSoul(String name, String coreIdentity, String currentState, String mood) {
        Soul soul = soulStore.load();
        if (name != null && !name.isBlank()) {
            soul.rename(name);
        }
        if (coreIdentity != null) {
            soul.rewriteIdentity(coreIdentity);
        }
        if (currentState != null) {
            soul.shiftState(currentState);
        }
        if (mood != null && !mood.isBlank()) {
            soul.shiftMood(Mood.valueOf(mood));
        }
        soulStore.save(soul);
        return getSoul();
    }

    /** The owner profile injected alongside the persona every turn. Same no-null contract as above. */
    public Map<String, Object> getUserProfile() {
        UserProfile profile = userProfileStore.load();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", orEmpty(profile.name));
        result.put("profile", orEmpty(profile.profile));
        result.put("updatedAt", orEmpty(profile.updatedAt));
        return result;
    }

    public Map<String, Object> saveUserProfile(String name, String profileText) {
        UserProfile profile = userProfileStore.load();
        if (name != null && !name.isBlank()) {
            profile.rename(name);
        }
        if (profileText != null) {
            profile.rewriteProfile(profileText);
        }
        userProfileStore.save(profile);
        return getUserProfile();
    }

    // ----------------------------------------------------------------- memory

    public Map<String, Object> getMemorySummary() {
        MemorySummary summary = memoryAdminService.getMemorySummary();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("embeddingSources", summary.embeddingSources());
        result.put("journalCount", summary.journalCount());
        result.put("chatMessageCount", summary.chatMessageCount());
        return result;
    }

    /** A capped sample of stored fact texts, for browsing what the agent has learned. */
    public List<String> getFacts(int limit) {
        return factStore.listFactTexts(limit <= 0 ? 25 : limit);
    }

    /**
     * Runs the same semantic search the retrieval augmentor runs before each reply, so the page
     * answers "would the agent recall this?" rather than doing its own keyword match.
     */
    public List<String> searchFacts(String query, int maxResults, double minScore) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return factStore.search(query, maxResults <= 0 ? 10 : maxResults, minScore);
    }

    /** Stores a fact exactly as the remember tool would, so it is indistinguishable from a learned one. */
    public String addFact(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Fact content must not be blank");
        }
        factStore.store(content, Map.of("source", MemorySource.REMEMBER_TOOL.value()));
        return "Stored";
    }

    public List<String> getFactSources() {
        return factStore.listSources();
    }

    public long purgeFactsBySource(String source) {
        return memoryAdminService.purgeEmbeddingsBySource(source);
    }

    public String purgeAllMemory() {
        memoryAdminService.purgeAllMemory();
        return "All memory purged";
    }

    public long reviewMemory() {
        return memoryReviewJob.reviewNow();
    }

    public boolean curateProfile() {
        return memoryCurationJob.curateProfile();
    }

    public String consolidateEpisodes() {
        episodicConsolidatorJob.consolidateNow();
        return "Episodic consolidation finished";
    }

    // ----------------------------------------------------------------- skills

    /** The skill index exactly as it is injected into the prompt: name plus description. */
    public List<Map<String, Object>> getSkills() {
        List<Map<String, Object>> skills = new ArrayList<>();
        for (SkillSummary summary : skillAdminService.listSkills()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", summary.name());
            entry.put("description", orEmpty(summary.description()));
            skills.add(entry);
        }
        return skills;
    }

    /** The full SKILL.md body, the same progressive-disclosure load the viewSkill tool performs. */
    public Map<String, Object> getSkill(String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        Skill skill = skillAdminService.getSkill(name).orElse(null);
        result.put("found", skill != null);
        result.put("name", skill == null ? orEmpty(name) : orEmpty(skill.name()));
        result.put("description", skill == null ? "" : orEmpty(skill.description()));
        result.put("body", skill == null ? "" : orEmpty(skill.body()));
        return result;
    }

    public boolean pinSkill(String name, boolean pinned) {
        return skillAdminService.setPinned(name, pinned);
    }

    public boolean deleteSkill(String name) {
        return skillAdminService.deleteSkill(name);
    }

    public long curateSkills() {
        return skillCurationJob.curateNow();
    }

    public int sweepSkillLifecycle() {
        return skillLifecycleJob.sweepNow();
    }

    // ------------------------------------------------------------------ tools

    /**
     * The tool beans registered this boot, each with the {@code @Tool} methods it actually exposes to
     * the model. The specifications come from the same langchain4j helper the tool provider uses, so
     * this is the model's own view of the toolbelt rather than a reflection-based approximation.
     */
    public List<Map<String, Object>> getRegisteredTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (Object proxy : registeredTools) {
            Class<?> beanClass = unwrap(proxy);
            List<Map<String, Object>> methods = new ArrayList<>();
            for (ToolSpecification spec : ToolSpecifications.toolSpecificationsFrom(beanClass)) {
                Map<String, Object> method = new LinkedHashMap<>();
                method.put("name", orEmpty(spec.name()));
                method.put("description", orEmpty(spec.description()));
                methods.add(method);
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("simpleName", beanClass.getSimpleName());
            entry.put("className", beanClass.getName());
            entry.put("packageName", beanClass.getPackageName());
            entry.put("methods", methods);
            tools.add(entry);
        }
        tools.sort((a, b) -> String.valueOf(a.get("simpleName")).compareTo(String.valueOf(b.get("simpleName"))));
        return tools;
    }

    // ------------------------------------------------------------ composition

    /** The manifest as it currently stands on disk, plus whether this build can write it back. */
    public Map<String, Object> getComposition() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("writable", compositionSource.isWritable());
        result.put("location", compositionSource.location());
        if (!compositionSource.isWritable()) {
            result.put("yaml", "");
            return result;
        }
        result.put("yaml", CompositionManifestParser.render(compositionSource.read()));
        return result;
    }

    /**
     * Selects or deselects one capability in the application's own agent.yml.
     *
     * <p>The edit is recorded, not applied. A capability decides a Maven dependency, and the pom
     * generator that reads this file runs in {@code generate-sources} - a phase {@code quarkus:dev}
     * does not re-enter on a resource change. Dev mode notices the file and declines to restart, so
     * the change takes effect on the next build: restart dev mode, or run {@code mvn qlawkus:generate}.
     */
    public Map<String, Object> setCapability(String capability, boolean enabled) {
        CompositionManifest updated = compositionSource.setCapability(capability, enabled);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capability", capability);
        result.put("enabled", enabled);
        result.put("yaml", CompositionManifestParser.render(updated));
        return result;
    }

    /** Replaces the whole manifest, rejecting anything that does not parse before touching disk. */
    public Map<String, Object> saveComposition(String yaml) {
        CompositionManifest updated = compositionSource.replace(yaml);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("yaml", CompositionManifestParser.render(updated));
        return result;
    }

    // ----------------------------------------------------------------- shared

    private static String orEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Class<?> unwrap(Object proxy) {
        Class<?> beanClass = ClientProxy.unwrap(proxy).getClass();
        while (beanClass.getName().contains("_Subclass")) {
            beanClass = beanClass.getSuperclass();
        }
        return beanClass;
    }
}
