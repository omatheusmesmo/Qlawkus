package dev.omatheusmesmo.qlawkus.deployment;

import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tool.ClawToolProvider;
import dev.omatheusmesmo.qlawkus.tool.ClawToolProviderSupplier;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class ClientProcessor {

    private static final String FEATURE = "qlawkus-client";
    private static final DotName CLAW_TOOL_ANNOTATION = DotName.createSimple(ClawTool.class.getName());

  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem(FEATURE);
  }

  @BuildStep
  AdditionalBeanBuildItem registerClawToolInfrastructure() {
    return AdditionalBeanBuildItem.builder()
      .addBeanClass(ClawToolProvider.class)
      .addBeanClass(ClawToolProviderSupplier.class)
      .setUnremovable()
      .build();
  }

  @BuildStep
    List<AdditionalBeanBuildItem> registerClawTools(CombinedIndexBuildItem combinedIndex) {
        Collection<AnnotationInstance> annotations = combinedIndex.getIndex().getAnnotations(CLAW_TOOL_ANNOTATION);

        List<AdditionalBeanBuildItem> items = new ArrayList<>();
        for (AnnotationInstance annotation : annotations) {
            if (annotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            String className = annotation.target().asClass().name().toString();
            items.add(AdditionalBeanBuildItem.unremovableOf(className));
        }
        return items;
    }

    @BuildStep
    ReflectiveClassBuildItem registerClawToolsForReflection(CombinedIndexBuildItem combinedIndex) {
        Collection<AnnotationInstance> annotations = combinedIndex.getIndex().getAnnotations(CLAW_TOOL_ANNOTATION);

        List<String> classNames = new ArrayList<>();
        for (AnnotationInstance annotation : annotations) {
            if (annotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            classNames.add(annotation.target().asClass().name().toString());
        }

        return ReflectiveClassBuildItem.builder(classNames)
                .methods()
                .fields()
                .build();
    }
}
