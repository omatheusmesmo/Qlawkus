package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class GoogleOAuthState extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 64)
    public String token;

    @Column(nullable = false)
    public Instant expiresAt;

    @Column(name = "memory_id")
    public String memoryId;

    @Column(name = "provider_id")
    public String providerId;

    @Column(name = "chat_id")
    public String chatId;

    public static GoogleOAuthState findByToken(String token) {
        return find("token = ?1", token).firstResult();
    }

    public static long deleteExpired() {
        return delete("expiresAt < ?1", Instant.now());
    }
}
