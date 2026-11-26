package dev.omatheusmesmo.qlawkus.tools.skillhub;

/**
 * One file of a skill's {@code scripts/} folder, before it is installed. {@code path} is relative to that
 * folder. {@code content} is set when the bytes were already read (an archive); otherwise {@code location}
 * is the URL they are fetched from when the skill is installed.
 */
public record SkillScript(String path, String location, byte[] content) {
}
