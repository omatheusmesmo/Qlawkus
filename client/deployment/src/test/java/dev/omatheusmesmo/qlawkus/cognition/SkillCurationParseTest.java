package dev.omatheusmesmo.qlawkus.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillCurationParseTest {

  private static final List<SkillSummary> KNOWN = List.of(
      new SkillSummary("deploy-site", "deploy"),
      new SkillSummary("deploy-website", "deploy too"),
      new SkillSummary("triage-inbox", "triage"));

  @Test
  void none_yieldsEmpty() {
    assertTrue(SkillCurationJob.parseNames("NONE", KNOWN).isEmpty());
    assertTrue(SkillCurationJob.parseNames(null, KNOWN).isEmpty());
  }

  @Test
  void returnsOnlyKnownNames_strippingBullets() {
    List<String> names = SkillCurationJob.parseNames(
        "- deploy-website\n- not-a-real-skill", KNOWN);
    assertEquals(List.of("deploy-website"), names);
  }

  @Test
  void parsesPlainLines() {
    List<String> names = SkillCurationJob.parseNames("deploy-website\ntriage-inbox", KNOWN);
    assertEquals(List.of("deploy-website", "triage-inbox"), names);
  }
}
