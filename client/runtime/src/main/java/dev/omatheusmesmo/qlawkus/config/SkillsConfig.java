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
}
