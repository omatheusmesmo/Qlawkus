package dev.omatheusmesmo.qlawkus.tool.access;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridges the build-time classification to runtime: the build step records a call to {@link #create}
 * with parallel lists, which serialize into bytecode, and the {@link ToolAccessRegistry} bean is baked
 * from them.
 */
@Recorder
public class ToolAccessRecorder {

    public RuntimeValue<ToolAccessRegistry> create(List<String> tools, List<AccessClass> classes,
            List<AccessSource> sources, List<String> origins) {
        List<ToolAccessEntry> entries = new ArrayList<>();
        for (int i = 0; i < tools.size(); i++) {
            entries.add(new ToolAccessEntry(tools.get(i), classes.get(i), sources.get(i), origins.get(i)));
        }
        return new RuntimeValue<>(new ToolAccessRegistry(entries));
    }
}
