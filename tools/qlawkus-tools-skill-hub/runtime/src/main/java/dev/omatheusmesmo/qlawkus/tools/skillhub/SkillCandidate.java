package dev.omatheusmesmo.qlawkus.tools.skillhub;

/**
 * One skill a source holds, before it is installed: a folder name in a repository or archive, or the
 * single skill a link names. {@code content} is set when the text was already read (archives, a named
 * skill); otherwise {@code location} is the URL {@link SkillHub#installCandidate} fetches.
 */
public record SkillCandidate(String name, String source, String location, String content) {
}
