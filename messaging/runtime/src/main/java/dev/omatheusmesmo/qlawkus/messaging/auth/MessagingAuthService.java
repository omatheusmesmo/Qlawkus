package dev.omatheusmesmo.qlawkus.messaging.auth;

import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
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
        return allowed.contains(message.userId());
    }
}
