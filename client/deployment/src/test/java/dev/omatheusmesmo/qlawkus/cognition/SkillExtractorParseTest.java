package dev.omatheusmesmo.qlawkus.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SkillExtractorParseTest {

  @Test
  void none_yieldsEmpty() {
    assertTrue(SkillExtractorObserver.parse("NONE").isEmpty());
    assertTrue(SkillExtractorObserver.parse("").isEmpty());
    assertTrue(SkillExtractorObserver.parse(null).isEmpty());
  }

  @Test
  void nonSkillResponse_yieldsEmpty() {
    assertTrue(SkillExtractorObserver.parse("Sure, here is some chatter.").isEmpty());
  }

  @Test
  void parsesNameDescriptionAndBody() {
    String response = """
        SKILL: deploy-site
        DESCRIPTION: Deploy the static site
        ---
        1. Build with mvn
        2. Push to pages""";
    Optional<Skill> skill = SkillExtractorObserver.parse(response);
    assertTrue(skill.isPresent());
    assertEquals("deploy-site", skill.get().name());
    assertEquals("Deploy the static site", skill.get().description());
    assertTrue(skill.get().body().contains("Push to pages"));
  }

  @Test
  void missingName_yieldsEmpty() {
    assertTrue(SkillExtractorObserver.parse("SKILL:\nDESCRIPTION: x\n---\nbody").isEmpty());
  }
}
