package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.Instant;

@Entity
public class GoogleCredential extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 64)
    public String provider;

    @Column(columnDefinition = "TEXT", nullable = false)
    public String encryptedRefreshToken;

    @Column(columnDefinition = "TEXT")
    public String accessToken;

    @Column(length = 32)
    public String tokenType;

    public Instant expiresAt;

    public Instant createdAt;

    public Instant updatedAt;

    public static GoogleCredential findByProvider(String provider) {
        return find("provider = ?1", provider).firstResult();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
