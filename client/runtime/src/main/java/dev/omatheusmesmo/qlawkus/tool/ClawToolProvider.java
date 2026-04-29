package dev.omatheusmesmo.qlawkus.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ClawToolProvider implements ToolProvider {

    @Inject
    @ClawTool
    public Instance<Object> extensionTools;

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        if (extensionTools.isUnsatisfied()) {
            Log.debug("No @ClawTool extension tools found");
            return new ToolProviderResult(Map.of());
        }

        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        for (Object tool : extensionTools) {
            List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(tool);
            for (ToolSpecification spec : specs) {
                Method toolMethod = findToolMethod(tool, spec);
                builder.add(spec, new DefaultToolExecutor(tool, toolMethod));
                Log.debugf("Registered extension tool: %s from %s", spec.name(), tool.getClass().getSimpleName());
            }
        }
        return builder.build();
    }

    private Method findToolMethod(Object tool, ToolSpecification spec) {
        return Arrays.stream(tool.getClass().getDeclaredMethods())
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
