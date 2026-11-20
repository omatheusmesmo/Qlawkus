package dev.omatheusmesmo.qlawkus.skill;

/**
 * Where an installed skill came from: the source string the owner or the registry named, the folder
 * name of the skill inside that source when it was one of several (null for a single-skill source),
 * and when it was installed (epoch milliseconds). Recorded so the skill can be updated from the same
 * source later.
 */
public record SkillOrigin(String source, String candidate, long installedEpochMs) {
}
