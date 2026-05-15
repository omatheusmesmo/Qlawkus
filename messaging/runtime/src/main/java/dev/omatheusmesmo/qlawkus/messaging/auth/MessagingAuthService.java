package dev.omatheusmesmo.qlawkus.messaging.auth;

import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class MessagingAuthService {

    @Inject
    MessagingAuthConfig config;

    public boolean isAuthorized(MessagingMessage message) {
        List<String> allowed = config.allowedUsers().get(message.providerId());
        if (allowed == null || allowed.isEmpty()) {
            Log.warnf("MessagingAuth: no allowlist configured for provider=%s, denying user=%s",
                    message.providerId(), message.userId());
            return false;
        }
        boolean authorized = allowed.contains(message.userId());
        if (!authorized) {
            Log.warnf("MessagingAuth: unauthorized user=%s provider=%s", message.userId(), message.providerId());
        }
        return authorized;
    }
}
