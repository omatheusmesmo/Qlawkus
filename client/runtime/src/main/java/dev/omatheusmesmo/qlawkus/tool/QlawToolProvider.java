package dev.omatheusmesmo.qlawkus.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkus.arc.ClientProxy;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class QlawToolProvider implements ToolProvider {

    @Inject
    @QlawTool
    public Instance<Object> extensionTools;

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        if (extensionTools.isUnsatisfied()) {
            Log.debug("No @QlawTool extension tools found");
            return new ToolProviderResult(Map.of());
        }

        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        for (Object proxy : extensionTools) {
            Object tool = ClientProxy.unwrap(proxy);
            Class<?> beanClass = resolveBeanClass(tool);
            List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(beanClass);
            for (ToolSpecification spec : specs) {
                Method toolMethod = findToolMethod(beanClass, spec);
                builder.add(spec, new DefaultToolExecutor(tool, toolMethod));
                Log.debugf("Registered extension tool: %s from %s", spec.name(), beanClass.getSimpleName());
            }
        }
        return builder.build();
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
