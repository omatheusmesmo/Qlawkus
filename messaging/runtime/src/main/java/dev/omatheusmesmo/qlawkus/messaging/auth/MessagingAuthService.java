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

    public static final String WILDCARD = "*";

    public boolean isAuthorized(MessagingMessage message) {
        List<String> allowed = config.allowedUsers().get(message.providerId());
        if (allowed == null || allowed.isEmpty() || allowed.contains(WILDCARD)) {
            return true;
        }
        boolean authorized = allowed.contains(message.userId());
        if (!authorized) {
            Log.warnf("MessagingAuth: unauthorized user=%s provider=%s", message.userId(), message.providerId());
        }
        return authorized;
    }
}
