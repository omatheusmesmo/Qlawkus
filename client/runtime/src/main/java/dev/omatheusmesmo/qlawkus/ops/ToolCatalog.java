package dev.omatheusmesmo.qlawkus.ops;

import dev.omatheusmesmo.qlawkus.tool.QlawToolProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Read-only names of tools the agent would expose on the next turn, for the ops dashboard.
 */
@ApplicationScoped
public class ToolCatalog {

    private final QlawToolProvider provider;

    @Inject
    ToolCatalog(QlawToolProvider provider) {
        this.provider = provider;
    }

    public List<String> toolNames() {
        return provider.provideTools(null).tools().keySet().stream()
                .map(spec -> spec.name())
                .sorted()
                .toList();
    }
}
