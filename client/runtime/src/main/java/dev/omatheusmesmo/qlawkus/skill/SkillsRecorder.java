package dev.omatheusmesmo.qlawkus.skill;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridges the build-time bundled-skill discovery to runtime. The build step parses the bundled
 * SKILL.md files at augmentation and records a call to {@link #create}, baking the parsed skills
 * into the {@link BundledSkills} bean. Parameters are plain {@code List<String>} so they serialize
 * cleanly into bytecode.
 */
@Recorder
public class SkillsRecorder {

  public RuntimeValue<BundledSkills> create(List<String> names, List<String> descriptions,
      List<String> bodies) {
    List<Skill> skills = new ArrayList<>();
    for (int i = 0; i < names.size(); i++) {
      skills.add(new Skill(names.get(i), descriptions.get(i), bodies.get(i)));
    }
    return new RuntimeValue<>(new BundledSkills(skills));
  }
}
