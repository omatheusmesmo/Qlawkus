package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;

/**
 * Runtime configuration for the skill subsystem (procedural memory).
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.skills")
public interface SkillsConfig {

    /**
     * Whether the skill subsystem is active (index injection, tools and stores).
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Directories scanned for SKILL.md skill folders, in precedence order. Every root is read;
     * the first one is the owned, writable root where the agent's self-authored skills are saved.
     * Defaults to an isolated qlawkus-owned directory so self-authored skills never pollute the
     * shared cross-agent {@code .agents/skills} on the host. Add {@code .agents/skills} here to
     * opt into cross-agent sharing (sensible inside a container).
     */
    @WithDefault("${user.home}/.qlawkus/skills")
    List<String> roots();

    /**
     * Maximum number of skills whose name and description are injected into the system prompt
     * each turn. Caps prompt growth as the library grows; the full body is always loadable on
     * demand via the {@code viewSkill} tool.
     */
    @WithDefault("50")
    int maxInjected();

    /**
     * Passive distillation: after each completed turn, mine a reusable procedure from the
     * conversation and save it as a skill.
     */
    Extractor extractor();

    /**
     * Background curation: periodically remove redundant (duplicate) skills.
     */
    Curation curation();

    interface Extractor {

        /**
         * Whether to distill a reusable skill from each completed conversation turn.
         */
        @WithDefault("true")
        boolean enabled();
    }

    interface Curation {

        /**
         * Whether the scheduled curation job removes redundant skills.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Cron expression for the scheduled skill-curation job.
         */
        @WithDefault("0 50 3 * * ?")
        String cron();
    }
}
