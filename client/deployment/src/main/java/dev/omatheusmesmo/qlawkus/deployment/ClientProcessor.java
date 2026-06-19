package dev.omatheusmesmo.qlawkus.deployment;

import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tool.QlawToolProvider;
import dev.omatheusmesmo.qlawkus.tool.QlawToolProviderSupplier;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigBuilderBuildItem;
import io.quarkus.deployment.builditem.StaticInitConfigBuilderBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import dev.omatheusmesmo.qlawkus.model.LlmKindConfigBuilder;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class ClientProcessor {

    private static final String FEATURE = "qlawkus-client";
    private static final DotName CLAW_TOOL_ANNOTATION = DotName.createSimple(QlawTool.class.getName());
    private static final DotName DTO_PACKAGE = DotName.createSimple("dev.omatheusmesmo.qlawkus.dto");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerLlmKindConfigSource(BuildProducer<StaticInitConfigBuilderBuildItem> staticInit,
            BuildProducer<RunTimeConfigBuilderBuildItem> runTime) {
        staticInit.produce(new StaticInitConfigBuilderBuildItem(LlmKindConfigBuilder.class.getName()));
        runTime.produce(new RunTimeConfigBuilderBuildItem(LlmKindConfigBuilder.class.getName()));
    }

    @BuildStep
    AdditionalBeanBuildItem registerQlawToolInfrastructure() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QlawToolProvider.class)
                .addBeanClass(QlawToolProviderSupplier.class)
                .setUnremovable()
                .build();
    }

    @BuildStep
    List<AdditionalBeanBuildItem> registerQlawTools(CombinedIndexBuildItem combinedIndex) {
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
    ReflectiveClassBuildItem registerQlawToolsForReflection(CombinedIndexBuildItem combinedIndex) {
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

    @BuildStep
    ReflectiveClassBuildItem registerDtosForReflection(CombinedIndexBuildItem combinedIndex) {
        Collection<ClassInfo> dtoClasses = combinedIndex.getIndex()
                .getKnownDirectSubclasses(DotName.createSimple("java.lang.Record"));

        List<String> classNames = new ArrayList<>();
        for (ClassInfo classInfo : dtoClasses) {
            if (classInfo.name().packagePrefix().equals(DTO_PACKAGE.toString())) {
                classNames.add(classInfo.name().toString());
            }
        }

        return ReflectiveClassBuildItem.builder(classNames)
                .methods()
                .fields()
                .build();
    }
}
