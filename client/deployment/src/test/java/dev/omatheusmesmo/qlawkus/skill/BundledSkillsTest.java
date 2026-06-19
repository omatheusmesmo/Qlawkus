package dev.omatheusmesmo.qlawkus.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BundledSkillsTest {

  @Test
  void mergedIndex_runtimeWins_thenBundledAppended() {
    BundledSkills bundled = new BundledSkills(List.of(
        new Skill("a", "bundled-a", "x"),
        new Skill("c", "bundled-c", "y")));
    List<SkillSummary> runtime = List.of(
        new SkillSummary("a", "runtime-a"),
        new SkillSummary("b", "runtime-b"));

    List<SkillSummary> merged = bundled.mergedIndex(runtime);

    assertEquals(3, merged.size());
    assertEquals("runtime-a", descriptionOf(merged, "a"));
    assertEquals("runtime-b", descriptionOf(merged, "b"));
    assertEquals("bundled-c", descriptionOf(merged, "c"));
  }

  @Test
  void get_findsBundled_orEmpty() {
    BundledSkills bundled = new BundledSkills(List.of(new Skill("a", "desc", "body")));
    assertTrue(bundled.get("a").isPresent());
    assertTrue(bundled.get("missing").isEmpty());
  }

  private static String descriptionOf(List<SkillSummary> list, String name) {
    return list.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow().description();
  }
}
