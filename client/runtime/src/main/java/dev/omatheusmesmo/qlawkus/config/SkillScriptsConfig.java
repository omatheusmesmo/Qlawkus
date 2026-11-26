package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;

/**
 * Runtime configuration for the scripts a skill carries in its {@code scripts/} folder.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.skill-scripts")
public interface SkillScriptsConfig {

    /**
     * Whether the agent may run the scripts of its skills ({@code runSkillScript} and
     * {@code runInstalledSkillScript}). When {@code false} both tools refuse.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Longest a skill script may run. A longer timeout asked for by the model is lowered to this, and a
     * call that names none gets this one.
     */
    @WithDefault("60s")
    Duration timeout();

    /**
     * Whether every run of a script from a skill installed by the Skill Hub waits for the owner's
     * approval, whatever {@code qlawkus.skill-hub.approval-mode} says: the install approval covers
     * writing the files, not running code someone else wrote. Scripts of the agent's own skills, or ones
     * the owner placed by hand, never wait.
     */
    @WithDefault("true")
    boolean installedRequireApproval();
}
