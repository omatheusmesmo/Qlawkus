package dev.omatheusmesmo.qlawkus.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillIndexRendererTest {

  @Test
  void emptyList_rendersNothing() {
    assertEquals("", SkillIndexRenderer.render(List.of(), 50));
  }

  @Test
  void rendersNameAndDescriptionBullets() {
    String block = SkillIndexRenderer.render(
        List.of(new SkillSummary("triage-inbox", "Triage the owner's inbox")), 50);
    assertTrue(block.contains("## Skills"));
    assertTrue(block.contains("- **triage-inbox**: Triage the owner's inbox"));
    assertTrue(block.contains("viewSkill"));
  }

  @Test
  void capsAtMaxAndNotesRemainder() {
    List<SkillSummary> skills = List.of(
        new SkillSummary("a", "first"),
        new SkillSummary("b", "second"),
        new SkillSummary("c", "third"));
    String block = SkillIndexRenderer.render(skills, 2);
    assertTrue(block.contains("**a**"));
    assertTrue(block.contains("**b**"));
    assertTrue(!block.contains("**c**"));
    assertTrue(block.contains("1 more"));
  }
}
