package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@ApplicationScoped
public class GoogleOAuthStateStore {

    private static final long EXPIRY_SECONDS = 600;
    private SecureRandom random;

    @PostConstruct
    void init() {
        this.random = new SecureRandom();
    }

    @Transactional
    public String issue() {
        return issue(null, null, null);
    }

    @Transactional
    public String issue(String memoryId, String providerId, String chatId) {
        GoogleOAuthState.deleteExpired();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        GoogleOAuthState entity = new GoogleOAuthState();
        entity.token = token;
        entity.expiresAt = Instant.now().plusSeconds(EXPIRY_SECONDS);
        entity.memoryId = memoryId;
        entity.providerId = providerId;
        entity.chatId = chatId;
        entity.persist();

        Log.debugf("GoogleOAuthStateStore: issued state token (memoryId=%s provider=%s chatId=%s)",
                memoryId, providerId, chatId);
        return token;
    }

    @Transactional
    public GoogleOAuthState consume(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        GoogleOAuthState entity = GoogleOAuthState.findByToken(token);
        if (entity == null) {
            Log.warn("GoogleOAuthStateStore: unknown state token");
            return null;
        }
        entity.delete();
        if (entity.expiresAt.isBefore(Instant.now())) {
            Log.warn("GoogleOAuthStateStore: expired state token");
            return null;
        }
        return entity;
    }

}
