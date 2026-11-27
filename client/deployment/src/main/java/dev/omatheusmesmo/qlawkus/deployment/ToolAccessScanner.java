package dev.omatheusmesmo.qlawkus.deployment;

import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import dev.omatheusmesmo.qlawkus.tool.access.AccessClassInference;
import dev.omatheusmesmo.qlawkus.tool.access.AccessSource;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccess;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccessEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;

/**
 * Finds every {@code @Tool} method of every {@code @QlawTool} bean in the index and decides its
 * {@link AccessClass}: a {@link ToolAccess} on the method, else one on the bean, else the leading verb
 * of the method name, else {@link AccessClass#EXECUTE}. The result is the same for the same sources,
 * so the build report can be diffed and pinned by a test.
 */
public final class ToolAccessScanner {

    private static final DotName QLAW_TOOL = DotName.createSimple(QlawTool.class.getName());
    private static final DotName TOOL = DotName.createSimple(Tool.class.getName());
    private static final DotName TOOL_ACCESS = DotName.createSimple(ToolAccess.class.getName());

    private ToolAccessScanner() {
    }

    /**
     * @throws IllegalStateException when two tools share a name, since the model and the registry both
     *         address a tool by name alone
     */
    public static List<ToolAccessEntry> scan(IndexView index) {
        Map<String, ToolAccessEntry> byTool = new TreeMap<>();
        for (AnnotationInstance marker : index.getAnnotations(QLAW_TOOL)) {
            if (marker.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            ClassInfo bean = marker.target().asClass();
            Optional<AccessClass> beanLevel = beanLevelClass(index, bean);
            for (MethodInfo method : toolMethods(index, bean)) {
                ToolAccessEntry entry = classify(bean, method, beanLevel);
                ToolAccessEntry previous = byTool.putIfAbsent(entry.tool(), entry);
                if (previous != null) {
                    throw new IllegalStateException("Tool name '" + entry.tool() + "' is declared by both "
                            + previous.origin() + " and " + entry.origin() + "; tool names must be unique");
                }
            }
        }
        return new ArrayList<>(byTool.values());
    }

    private static ToolAccessEntry classify(ClassInfo bean, MethodInfo method, Optional<AccessClass> beanLevel) {
        String tool = toolName(method);
        String origin = bean.name() + "#" + method.name();
        AnnotationInstance declared = method.annotation(TOOL_ACCESS);
        if (declared != null) {
            return new ToolAccessEntry(tool, accessClass(declared), AccessSource.DECLARED, origin);
        }
        if (beanLevel.isPresent()) {
            return new ToolAccessEntry(tool, beanLevel.get(), AccessSource.DECLARED, origin);
        }
        Optional<AccessClass> inferred = AccessClassInference.fromMethodName(method.name());
        return inferred
                .map(accessClass -> new ToolAccessEntry(tool, accessClass, AccessSource.INFERRED, origin))
                .orElseGet(() -> new ToolAccessEntry(tool, AccessClass.EXECUTE, AccessSource.FALLBACK, origin));
    }

    private static String toolName(MethodInfo method) {
        AnnotationInstance tool = method.annotation(TOOL);
        if (tool.value("name") != null && !tool.value("name").asString().isBlank()) {
            return tool.value("name").asString();
        }
        return method.name();
    }

    private static AccessClass accessClass(AnnotationInstance access) {
        return AccessClass.valueOf(access.value().asEnum());
    }

    private static Optional<AccessClass> beanLevelClass(IndexView index, ClassInfo bean) {
        for (ClassInfo type : hierarchy(index, bean)) {
            AnnotationInstance declared = type.declaredAnnotation(TOOL_ACCESS);
            if (declared != null) {
                return Optional.of(accessClass(declared));
            }
        }
        return Optional.empty();
    }

    private static List<MethodInfo> toolMethods(IndexView index, ClassInfo bean) {
        List<MethodInfo> methods = new ArrayList<>();
        for (ClassInfo type : hierarchy(index, bean)) {
            for (MethodInfo method : type.methods()) {
                if (method.hasDeclaredAnnotation(TOOL)) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    private static List<ClassInfo> hierarchy(IndexView index, ClassInfo bean) {
        List<ClassInfo> types = new ArrayList<>();
        ClassInfo current = bean;
        while (current != null) {
            types.add(current);
            current = current.superName() == null ? null : index.getClassByName(current.superName());
        }
        return types;
    }
}
