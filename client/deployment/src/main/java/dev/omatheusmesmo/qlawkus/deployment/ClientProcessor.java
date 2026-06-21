package dev.omatheusmesmo.qlawkus.deployment;

import dev.langchain4j.skills.FileSystemSkill;
import dev.langchain4j.skills.FileSystemSkillLoader;
import dev.omatheusmesmo.qlawkus.skill.BundledSkills;
import dev.omatheusmesmo.qlawkus.skill.SkillsRecorder;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tool.QlawToolProvider;
import dev.omatheusmesmo.qlawkus.tool.QlawToolProviderSupplier;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigBuilderBuildItem;
import io.quarkus.deployment.builditem.StaticInitConfigBuilderBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.logging.Log;
import dev.omatheusmesmo.qlawkus.model.LlmKindConfigBuilder;
import jakarta.inject.Singleton;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

class ClientProcessor {

    private static final String FEATURE = "qlawkus-client";
    private static final DotName CLAW_TOOL_ANNOTATION = DotName.createSimple(QlawTool.class.getName());
    private static final DotName DTO_PACKAGE = DotName.createSimple("dev.omatheusmesmo.qlawkus.dto");
    private static final String BUNDLED_SKILLS_PATH = "META-INF/qlawkus-skills";
    private static final String SKILL_FILE = "SKILL.md";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Discovers SKILL.md files bundled on the classpath (META-INF/qlawkus-skills) at build time,
     * parses their frontmatter, and bakes them into the BundledSkills bean via a recorder. No
     * classpath scanning or file reads happen at runtime.
     */
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    SyntheticBeanBuildItem bundledSkills(SkillsRecorder recorder, ApplicationArchivesBuildItem archives) {
        List<String> names = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
        List<String> bodies = new ArrayList<>();

        for (ApplicationArchive archive : archives.getAllApplicationArchives()) {
            for (Path root : archive.getResolvedPaths()) {
                Path dir = root.resolve(BUNDLED_SKILLS_PATH);
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.filter(path -> path.getFileName().toString().equals(SKILL_FILE))
                            .forEach(path -> readBundledSkill(path, names, descriptions, bodies));
                } catch (IOException e) {
                    Log.warnf(e, "Failed to scan bundled skills under %s", dir);
                }
            }
        }
        Log.infof("Bundled skills discovered at build time: %d", names.size());

        return SyntheticBeanBuildItem.configure(BundledSkills.class)
                .scope(Singleton.class)
                .runtimeValue(recorder.create(names, descriptions, bodies))
                .unremovable()
                .done();
    }

    private static void readBundledSkill(Path file, List<String> names, List<String> descriptions,
            List<String> bodies) {
        try {
            FileSystemSkill parsed = FileSystemSkillLoader.loadSkill(file.getParent());
            String name = (parsed.name() == null || parsed.name().isBlank())
                    ? file.getParent().getFileName().toString()
                    : parsed.name();
            names.add(name);
            descriptions.add(parsed.description() == null ? "" : parsed.description());
            bodies.add(parsed.content());
        } catch (RuntimeException e) {
            Log.warnf(e, "Failed to read bundled skill %s", file);
        }
    }

    /**
     * {@code ActiveMemoryAugmentor} holds a static {@code ContentInjector} whose prompt template is
     * not native-image safe to construct at image build time. Initialize the class at runtime so its
     * static initializer runs in the running image instead of failing the build.
     */
    @BuildStep
    RuntimeInitializedClassBuildItem runtimeInitActiveMemoryAugmentor() {
        return new RuntimeInitializedClassBuildItem(
                "dev.omatheusmesmo.qlawkus.cognition.ActiveMemoryAugmentor");
    }

    @BuildStep
    ReflectiveClassBuildItem registerSkillModelForReflection() {
        return ReflectiveClassBuildItem.builder(
                "dev.omatheusmesmo.qlawkus.skill.Skill",
                "dev.omatheusmesmo.qlawkus.skill.SkillSummary")
                .methods()
                .fields()
                .build();
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
