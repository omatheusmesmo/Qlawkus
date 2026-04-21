package dev.omatheusmesmo.qlawkus.cognition;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.LocalDateTime;

@Entity
@Cacheable
public class SoulEntity extends PanacheEntityBase {

    @Id
    public Long id;

    public String name;

    @Column(columnDefinition = "TEXT")
    public String coreIdentity;

    @Column(columnDefinition = "TEXT")
    public String currentState;

    @Enumerated(EnumType.STRING)
    public Mood mood;

    public LocalDateTime createdAt;

    public LocalDateTime updatedAt;

    public static SoulEntity findSoul() {
        return findById(1L);
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String toSystemMessage() {
        StringBuilder sb = new StringBuilder();

        sb.append("# ").append(name).append("\n\n");
        sb.append(coreIdentity).append("\n\n");
        sb.append("---\n\n");
        sb.append("## Current State\n\n");
        sb.append(currentState).append("\n\n");
        sb.append("## Current Mood\n\n");
        sb.append("**").append(mood).append("** — ")
                .append(mood.getDescription()).append("\n\n");
        sb.append("*Adjust your approach based on your current mood ")
                .append("while staying true to your core identity.*");

        return sb.toString();
    }
}
