package dev.omatheusmesmo.qlawkus.agent.scope;

import dev.langchain4j.agentic.scope.AgenticScopePersister;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class AgenticScopeStoreInitializer {

    @Inject
    JpaAgenticScopeStore store;

    void onStartup(@Observes StartupEvent event) {
        AgenticScopePersister.INSTANCE.setStore(store);
        Log.info("AgenticScopePersister initialized with JPA-backed store");
    }
}
