package dev.omatheusmesmo.qlawkus.health;

import dev.omatheusmesmo.qlawkus.config.SkillsConfig;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.SoulStore;
import dev.omatheusmesmo.qlawkus.store.UserProfileStore;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

/**
 * Reports whether the agent can run a turn, and which stores it would run one without.
 *
 * <p>The agent is single-instance, so DOWN does not move traffic to another replica: it means nobody
 * answers. Only what a turn cannot do without is therefore blocking. {@code SoulEngine} reads the
 * persona, the owner profile and the skill index to build every system message, and every turn
 * appends to working memory. Writing facts, journals, skills, the persona and the profile happens in
 * tools and background jobs; when one of those cannot be written the agent still answers, it just
 * stops learning, so the check stays UP and names the store under {@code degraded}. The skill
 * probes run only while {@code qlawkus.skills.enabled} is on, since a turn reads the index only then.
 * An instance that is already DOWN skips the write probes: they could not change the verdict, and
 * against a database that hangs rather than refuses, each one would wait out a connection timeout.
 *
 * <p>Each probe goes through a store SPI rather than inspecting a directory or a table, which is what
 * makes the check correct for every backend without a single conditional: markdown creates and
 * deletes a probe file, pgvector asks the server whether the session could write, hybrid does both.
 * A check written against one backend's storage would report a false failure on the others, and the
 * backend is chosen at build time. Reading alone is not enough: a volume mounted read-only, or owned
 * by another uid with world-readable files, passes every {@code load()} and fails on the first write.
 */
@Readiness
public class CognitionReadinessCheck implements HealthCheck {

    static final String NAME = "qlawkus-cognition";

    @Inject
    SoulStore soulStore;

    @Inject
    UserProfileStore userProfileStore;

    @Inject
    SkillStore skillStore;

    @Inject
    WorkingMemoryStore workingMemoryStore;

    @Inject
    FactStore factStore;

    @Inject
    EpisodicStore episodicStore;

    @Inject
    SkillsConfig skillsConfig;

    @Override
    public HealthCheckResponse call() {
        boolean skills = skillsConfig.enabled();
        Map<String, String> failures = new LinkedHashMap<>();
        probe(failures, "soul", soulStore::load);
        probe(failures, "owner", userProfileStore::load);
        if (skills) {
            probe(failures, "skills", skillStore::index);
        }
        probe(failures, "workingMemory", workingMemoryStore::verifyWritable);
        if (!failures.isEmpty()) {
            HealthCheckResponseBuilder down = HealthCheckResponse.named(NAME).down();
            failures.forEach(down::withData);
            return down.build();
        }

        Map<String, Runnable> learning = new LinkedHashMap<>();
        learning.put("facts", factStore::verifyWritable);
        learning.put("episodic", episodicStore::verifyWritable);
        if (skills) {
            learning.put("skills", skillStore::verifyWritable);
        }
        learning.put("soul", soulStore::verifyWritable);
        learning.put("owner", userProfileStore::verifyWritable);
        List<String> degraded = new ArrayList<>();
        learning.forEach((store, write) -> {
            if (probe(failures, store, write)) {
                degraded.add(store);
            }
        });

        HealthCheckResponseBuilder up = HealthCheckResponse.named(NAME).up();
        failures.forEach(up::withData);
        if (!degraded.isEmpty()) {
            up.withData("degraded", String.join(",", degraded));
        }
        return up.build();
    }

    /** Runs one probe, recording its failure under {@code store}; returns whether it failed. */
    private static boolean probe(Map<String, String> failures, String store, Runnable probe) {
        try {
            probe.run();
            return false;
        } catch (RuntimeException e) {
            failures.put(store, describe(e));
            return true;
        }
    }

    /**
     * Renders the failure as type plus message. The message alone is often empty on IO failures, and
     * the value is surfaced on an unauthenticated endpoint, so it stays a summary rather than a stack
     * trace.
     */
    private static String describe(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + message;
    }
}
