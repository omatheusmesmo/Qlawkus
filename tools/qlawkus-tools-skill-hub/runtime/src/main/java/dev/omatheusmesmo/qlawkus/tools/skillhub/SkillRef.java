package dev.omatheusmesmo.qlawkus.tools.skillhub;

/**
 * A skill discovered in a remote registry: enough to show a search hit and to install it.
 *
 * <p>{@code source} is the registry-resolvable install reference - an {@code owner/repo} slug, a git
 * URL, or a host that serves {@code /.well-known/agent-skills/index.json} (the agentskills.io
 * standard). It is the value passed back to {@link SkillHub#install(String)}.
 */
public record SkillRef(String name, String description, String source) {
}
