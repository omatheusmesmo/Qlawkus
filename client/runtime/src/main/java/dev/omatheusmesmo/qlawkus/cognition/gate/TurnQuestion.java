package dev.omatheusmesmo.qlawkus.cognition.gate;

/**
 * A yes/no question a post-turn generation asks before it runs.
 *
 * @param id a short, stable, kebab-case name, used as a metric tag, so the set of ids stays bounded
 * @param text the question itself, in plain English
 */
public record TurnQuestion(String id, String text) {

  public TurnQuestion {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("question id must not be blank");
    }
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("question text must not be blank");
    }
  }
}
