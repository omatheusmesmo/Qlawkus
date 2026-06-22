package dev.omatheusmesmo.qlawkus.tools.skillhub.deployment;

import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillRef;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

/**
 * Build steps for the optional {@code qlawkus-skill-hub} extension: the remote skill-registry client
 * (search + install).
 * <p>
 * {@code HttpSkillHub} ({@code @DefaultBean}) and {@code SkillHubTool} ({@code @QlawTool}) carry CDI
 * scopes and are discovered from this extension's Jandex index - the tool is additionally registered
 * as an unremovable bean and for reflection by {@code ClientProcessor}'s {@code @QlawTool} scan. Only
 * {@link SkillRef} needs explicit reflection registration here (it crosses the tool boundary as data).
 */
class SkillHubProcessor {

  private static final String FEATURE = "qlawkus-skill-hub";

  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem(FEATURE);
  }

  @BuildStep
  ReflectiveClassBuildItem registerSkillRefForReflection() {
    return ReflectiveClassBuildItem.builder(SkillRef.class.getName())
        .methods()
        .fields()
        .build();
  }
}
