package dev.omatheusmesmo.qlawkus.tools.skillhub;

/**
 * A refusal for a size or count cap (the per-call install cap, a SKILL.md or archive size, the entries or
 * the expanded size of an archive). It is an {@link IllegalArgumentException} so existing callers still
 * see a refused input, and it lets the REST install endpoints report an over-cap request as such.
 */
final class SkillLimitException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  SkillLimitException(String message) {
    super(message);
  }
}
