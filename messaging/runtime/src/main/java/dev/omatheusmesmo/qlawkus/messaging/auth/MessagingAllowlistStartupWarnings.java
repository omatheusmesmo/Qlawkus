package dev.omatheusmesmo.qlawkus.messaging.auth;

import dev.omatheusmesmo.qlawkus.messaging.MessagingProvider;
import dev.omatheusmesmo.qlawkus.messaging.ProviderRegistry;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class MessagingAllowlistStartupWarnings {

    @Inject
    MessagingAuthConfig config;

    @Inject
    ProviderRegistry registry;

    List<String> activeProviderIds;

    void onStart(@Observes StartupEvent event) {
        activeProviderIds = registry.activeProviders().stream()
                .map(MessagingProvider::providerId)
                .toList();
        messages().forEach(Log::warn);
    }

    List<String> messages() {
        List<String> lines = new ArrayList<>();
        for (String providerId : activeProviderIds) {
            List<String> listed = MessagingAuthService.listedIds(config.allowedUsers(), providerId);
            if (listed.isEmpty()) {
                lines.add("Messaging channel " + providerId + " has no allowlist: every message is rejected until"
                        + " qlawkus.messaging.auth.allowed-users." + providerId + " lists a user id");
            } else if (listed.contains(MessagingAuthService.WILDCARD)) {
                lines.add("Messaging channel " + providerId + " accepts messages from anyone"
                        + " (qlawkus.messaging.auth.allowed-users." + providerId + "=*)");
            }
        }
        return lines;
    }
}
