package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@ApplicationScoped
public class GoogleOAuthStateStore {

    private static final long EXPIRY_SECONDS = 600;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public String issue() {
        GoogleOAuthState.deleteExpired();
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        GoogleOAuthState entity = new GoogleOAuthState();
        entity.token = token;
        entity.expiresAt = Instant.now().plusSeconds(EXPIRY_SECONDS);
        entity.persist();

        Log.debugf("GoogleOAuthStateStore: issued state token");
        return token;
    }

    @Transactional
    public boolean validateAndConsume(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        GoogleOAuthState entity = GoogleOAuthState.findByToken(token);
        if (entity == null) {
            Log.warn("GoogleOAuthStateStore: unknown state token");
            return false;
        }
        entity.delete();
        if (entity.expiresAt.isBefore(Instant.now())) {
            Log.warn("GoogleOAuthStateStore: expired state token");
            return false;
        }
        return true;
    }
}
