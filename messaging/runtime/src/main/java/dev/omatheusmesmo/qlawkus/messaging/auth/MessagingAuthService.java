package dev.omatheusmesmo.qlawkus.messaging.auth;

import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class MessagingAuthService {

    @Inject
    MessagingAuthConfig config;

    public static final String WILDCARD = "*";

    public boolean isAuthorized(MessagingMessage message) {
        List<String> allowed = listedIds(config.allowedUsers(), message.providerId());
        if (allowed.contains(WILDCARD)) {
            return true;
        }
        return message.userId() != null && allowed.contains(message.userId());
    }

    static List<String> listedIds(Map<String, Optional<List<String>>> allowedUsers, String providerId) {
        if (allowedUsers == null) {
            return List.of();
        }
        return allowedUsers.getOrDefault(providerId, Optional.empty()).orElse(List.of()).stream()
                .filter(id -> id != null && !id.isBlank())
                .toList();
    }
}
