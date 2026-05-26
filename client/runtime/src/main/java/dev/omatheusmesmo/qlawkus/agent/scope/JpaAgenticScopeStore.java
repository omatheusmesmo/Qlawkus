package dev.omatheusmesmo.qlawkus.agent.scope;

import dev.langchain4j.agentic.scope.AgenticScopeKey;
import dev.langchain4j.agentic.scope.AgenticScopeStore;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class JpaAgenticScopeStore implements AgenticScopeStore {

    @Override
    @Transactional
    public boolean save(AgenticScopeKey key, DefaultAgenticScope agenticScope) {
        try {
            String data = AgenticScopeEntity.serializeScope(agenticScope);
            AgenticScopeEntity.findOrCreate(key, data);
            Log.debugf("AgenticScope saved: agent=%s memory=%s chars=%d", key.agentId(), key.memoryId(), data.length());
            return true;
        } catch (Exception e) {
            Log.errorf(e, "AgenticScope save failed: agent=%s memory=%s", key.agentId(), key.memoryId());
            return false;
        }
    }

    @Override
    @Transactional
    public Optional<DefaultAgenticScope> load(AgenticScopeKey key) {
        AgenticScopeEntity entity = AgenticScopeEntity.findByKey(key.agentId(), String.valueOf(key.memoryId()));
        if (entity == null) {
            Log.debugf("AgenticScope not found: agent=%s memory=%s", key.agentId(), key.memoryId());
            return Optional.empty();
        }
        DefaultAgenticScope scope = entity.toScope();
        if (scope == null) {
            Log.warnf("AgenticScope deserialization returned null: agent=%s memory=%s", key.agentId(), key.memoryId());
            return Optional.empty();
        }
        Log.debugf("AgenticScope loaded: agent=%s memory=%s", key.agentId(), key.memoryId());
        return Optional.of(scope);
    }

    @Override
    @Transactional
    public boolean delete(AgenticScopeKey key) {
        boolean removed = AgenticScopeEntity.removeByKey(key.agentId(), String.valueOf(key.memoryId()));
        if (removed) {
            Log.debugf("AgenticScope deleted: agent=%s memory=%s", key.agentId(), key.memoryId());
        }
        return removed;
    }

    @Override
    public Set<AgenticScopeKey> getAllKeys() {
        return AgenticScopeEntity.allKeys();
    }
}
