package dev.omatheusmesmo.qlawkus.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.omatheusmesmo.qlawkus.agent.StreamedToolContext;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.metrics.MeteredToolExecutor;
import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import dev.omatheusmesmo.qlawkus.tracing.TracedToolExecutor;
import io.opentelemetry.api.trace.Tracer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class QlawToolProvider implements ToolProvider {

    @Inject
    @QlawTool
    public Instance<Object> extensionTools;

    @Inject
    public Instance<QlawToolContributor> contributors;

    @Inject
    AgentMeters meters;

    @Inject
    StreamedToolContext streamedTools;

    @Inject
    SecretRedactor redactor;

    @Inject
    Instance<Tracer> tracer;

    @ConfigProperty(name = "quarkus.langchain4j.tracing.include-tool-arguments", defaultValue = "false")
    boolean includeToolArguments;

    @ConfigProperty(name = "quarkus.langchain4j.tracing.include-tool-result", defaultValue = "false")
    boolean includeToolResult;

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        Set<String> names = new HashSet<>();
        if (!extensionTools.isUnsatisfied()) {
            for (Object proxy : extensionTools) {
                Object tool = ClientProxy.unwrap(proxy);
                Class<?> beanClass = resolveBeanClass(tool);
                List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(beanClass);
                for (ToolSpecification spec : specs) {
                    Method toolMethod = findToolMethod(beanClass, spec);
                    QlawTool declaration = beanClass.getAnnotation(QlawTool.class);
                    boolean capture = declaration == null || declaration.capturePayload();
                    add(builder, names, spec, new DefaultToolExecutor(tool, toolMethod), capture);
                    Log.debugf("Registered extension tool: %s from %s", spec.name(), beanClass.getSimpleName());
                }
            }
        }
        if (!contributors.isUnsatisfied()) {
            for (QlawToolContributor contributor : contributors) {
                ToolProviderResult contribution;
                try {
                    contribution = contributor.provideTools(request);
                } catch (RuntimeException e) {
                    Log.warnf("Tool contribution unavailable (%s)", e.getClass().getSimpleName());
                    continue;
                }
                contribution.tools().forEach((spec, executor) -> add(builder, names, spec, executor, false));
            }
        }
        return builder.build();
    }

    private void add(ToolProviderResult.Builder builder, Set<String> names, ToolSpecification spec,
            ToolExecutor executor, boolean capturePayload) {
        if (!names.add(spec.name())) {
            throw new IllegalStateException("Duplicate tool name: " + spec.name());
        }
        ToolExecutor bridged = new ContextBridgingToolExecutor(executor, streamedTools);
        builder.add(spec, traced(spec.name(), new MeteredToolExecutor(spec.name(), bridged, meters, redactor), capturePayload));
    }

    /**
     * Wraps the executor in a span when a tracer exists, which is exactly when the
     * {@code observability} capability is selected. The argument and result switches are upstream's
     * own for local tools. External contributions never capture payloads, even when those switches
     * are enabled.
     */
    private ToolExecutor traced(String name, ToolExecutor executor, boolean capturePayload) {
        if (!tracer.isResolvable()) {
            return executor;
        }
        return new TracedToolExecutor(name, executor, tracer.get(),
                capturePayload && includeToolArguments, capturePayload && includeToolResult);
    }

    private Class<?> resolveBeanClass(Object tool) {
        Class<?> clazz = tool.getClass();
        while (clazz.getName().contains("_Subclass")) {
            clazz = clazz.getSuperclass();
        }
        return clazz;
    }

    private Method findToolMethod(Class<?> beanClass, ToolSpecification spec) {
        return Arrays.stream(beanClass.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Tool.class))
                .filter(m -> {
                    Tool t = m.getAnnotation(Tool.class);
                    String name = t.name().isBlank() ? m.getName() : t.name();
                    return spec.name().equals(name);
                })
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No @Tool method found matching specification: " + spec.name()));
    }
}
