package dev.omatheusmesmo.qlawkus.messaging.auth;

import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import io.smallrye.config.Converters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.spi.Converter;

@ApplicationScoped
public class MessagingAuthService {

    @Inject
    MessagingAuthConfig config;

    public static final String WILDCARD = "*";
    private static final Converter<List<String>> IDS =
            Converters.newCollectionConverter(value -> value, ArrayList::new);

    public boolean isAuthorized(MessagingMessage message) {
        List<String> allowed = listedIds(config.allowedUsers(), message.providerId());
        if (allowed.contains(WILDCARD)) {
            return true;
        }
        return message.userId() != null && allowed.contains(message.userId());
    }

    static List<String> listedIds(Map<String, Optional<String>> allowedUsers, String providerId) {
        if (allowedUsers == null) {
            return List.of();
        }
        String raw = allowedUsers.getOrDefault(providerId, Optional.empty()).orElse("");
        List<String> ids = IDS.convert(raw);
        return (ids == null ? List.<String>of() : ids).stream()
                .filter(id -> id != null && !id.isBlank())
                .toList();
    }
}
