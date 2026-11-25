package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the one-time state tokens of a Google sign-in. They live in the Google database when it is
 * configured, and in memory when it is not: a token lasts ten minutes and is read back by the same
 * instance that issued it, so losing them on a restart only asks the owner to start the sign-in again.
 */
@ApplicationScoped
public class GoogleOAuthStateStore {

    private static final long EXPIRY_SECONDS = 600;
    private SecureRandom random;
    private final Map<String, GoogleOAuthState> inMemory = new ConcurrentHashMap<>();

    @Inject
    GoogleAuthDatabase database;

    @PostConstruct
    void init() {
        this.random = new SecureRandom();
    }

    public String issue() {
        return issue(null, null, null);
    }

    public String issue(String memoryId, String providerId, String chatId) {
        GoogleOAuthState entity = newState(memoryId, providerId, chatId);
        if (database.isActive()) {
            database.inTransaction(() -> persist(entity));
        } else {
            inMemory.values().removeIf(state -> state.expiresAt.isBefore(Instant.now()));
            inMemory.put(entity.token, entity);
        }
        Log.debugf("GoogleOAuthStateStore: issued state token (memoryId=%s provider=%s chatId=%s)",
                memoryId, providerId, chatId);
        return entity.token;
    }

    public GoogleOAuthState consume(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        GoogleOAuthState entity = database.isActive()
                ? database.inTransaction(() -> takeFromDatabase(token))
                : inMemory.remove(token);
        if (entity == null) {
            Log.warn("GoogleOAuthStateStore: unknown state token");
            return null;
        }
        if (entity.expiresAt.isBefore(Instant.now())) {
            Log.warn("GoogleOAuthStateStore: expired state token");
            return null;
        }
        return entity;
    }

    private GoogleOAuthState newState(String memoryId, String providerId, String chatId) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        GoogleOAuthState entity = new GoogleOAuthState();
        entity.token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        entity.expiresAt = Instant.now().plusSeconds(EXPIRY_SECONDS);
        entity.memoryId = memoryId;
        entity.providerId = providerId;
        entity.chatId = chatId;
        return entity;
    }

    private Object persist(GoogleOAuthState entity) {
        GoogleOAuthState.deleteExpired();
        entity.persist();
        return entity;
    }

    private GoogleOAuthState takeFromDatabase(String token) {
        GoogleOAuthState entity = GoogleOAuthState.findByToken(token);
        if (entity != null) {
            entity.delete();
        }
        return entity;
    }
}
