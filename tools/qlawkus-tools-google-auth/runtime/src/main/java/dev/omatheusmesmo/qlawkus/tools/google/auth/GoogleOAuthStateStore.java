package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks pending Google OAuth state tokens so callbacks can be verified against requests we issued.
 * State tokens expire after 10 minutes to limit replay risk.
 */
@ApplicationScoped
public class GoogleOAuthStateStore {

    private static final long EXPIRY_SECONDS = 600;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Instant> pending = new ConcurrentHashMap<>();

    public String issue() {
        cleanupExpired();
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.put(token, Instant.now().plusSeconds(EXPIRY_SECONDS));
        Log.debugf("GoogleOAuthStateStore: issued state token, %d pending", pending.size());
        return token;
    }

    public boolean validateAndConsume(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Instant expiry = pending.remove(token);
        if (expiry == null) {
            Log.warn("GoogleOAuthStateStore: unknown state token");
            return false;
        }
        if (expiry.isBefore(Instant.now())) {
            Log.warn("GoogleOAuthStateStore: expired state token");
            return false;
        }
        return true;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        pending.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
