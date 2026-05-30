package dev.omatheusmesmo.qlawkus.agent.scope;

import dev.langchain4j.agentic.scope.AgenticScopeKey;
import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.logging.Log;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "agentic_scope")
@IdClass(AgenticScopeEntity.Key.class)
public class AgenticScopeEntity extends PanacheEntityBase {

    @Id
    @Column(name = "agent_id", nullable = false)
    public String agentId;

    @Id
    @Column(name = "memory_id", nullable = false)
    public String memoryId;

    @Column(name = "scope_data", columnDefinition = "TEXT")
    public String scopeData;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Transactional
    public static AgenticScopeEntity findOrCreate(AgenticScopeKey key, String data) {
        AgenticScopeEntity entity = findByKey(key.agentId(), String.valueOf(key.memoryId()));
        if (entity == null) {
            entity = new AgenticScopeEntity();
            entity.agentId = key.agentId();
            entity.memoryId = String.valueOf(key.memoryId());
            entity.createdAt = Instant.now();
            entity.scopeData = data;
            entity.updatedAt = Instant.now();
            entity.persist();
        } else {
            entity.scopeData = data;
            entity.updatedAt = Instant.now();
        }
        return entity;
    }

    public static AgenticScopeEntity findByKey(String agentId, String memoryId) {
        return find("agentId = ?1 AND memoryId = ?2", agentId, memoryId).firstResult();
    }

    @Transactional
    public static boolean removeByKey(String agentId, String memoryId) {
        return delete("agentId = ?1 AND memoryId = ?2", agentId, memoryId) > 0;
    }

    public static Set<AgenticScopeKey> allKeys() {
        return streamAll()
                .map(e -> (AgenticScopeEntity) e)
                .map(e -> new AgenticScopeKey(e.agentId, e.memoryId))
                .collect(Collectors.toSet());
    }

    public DefaultAgenticScope toScope() {
        if (scopeData == null) return null;
        try {
            return AgenticScopeSerializer.fromJson(scopeData);
        } catch (Exception e) {
            Log.errorf(e, "Failed to deserialize AgenticScope agent=%s memory=%s", agentId, memoryId);
            return null;
        }
    }

    public static String serializeScope(DefaultAgenticScope scope) {
        return AgenticScopeSerializer.toJson(scope);
    }

    public record Key(String agentId, String memoryId) implements java.io.Serializable {}
}
