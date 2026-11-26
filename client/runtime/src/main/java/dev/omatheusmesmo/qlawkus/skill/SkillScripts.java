package dev.omatheusmesmo.qlawkus.skill;

import java.nio.file.Path;

/**
 * The {@code scripts/} folder of a skill on disk. {@code installed} is true when the Skill Hub wrote it, which
 * it records with a {@link #INSTALLED_MARKER} file inside the folder. Scripts the owner placed by hand, or the
 * agent wrote itself, have no marker, and that difference decides which tool may run them and whether the
 * owner is asked first.
 */
public record SkillScripts(Path dir, boolean installed) {

  /** File the Skill Hub writes inside {@code scripts/}; it names the source the scripts were fetched from. */
  public static final String INSTALLED_MARKER = ".qlawkus-installed";
}
