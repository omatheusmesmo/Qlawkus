package dev.omatheusmesmo.qlawkus.tool;

import dev.langchain4j.service.tool.ToolProvider;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Supplier;

@ApplicationScoped
public class ClawToolProviderSupplier implements Supplier<ToolProvider> {

    @Override
    public ToolProvider get() {
        return Arc.container().instance(ClawToolProvider.class).get();
    }
}
