package dev.omatheusmesmo.qlawkus.messaging.devui;

import dev.omatheusmesmo.qlawkus.messaging.MessagingConfig;
import dev.omatheusmesmo.qlawkus.messaging.MessagingProvider;
import dev.omatheusmesmo.qlawkus.messaging.ProviderRegistry;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs the messaging card. A provider can be on the classpath yet inactive - the registry filters
 * by {@code qlawkus.messaging.provider.<id>.enabled} at startup - so the card reports both the
 * adapters that were compiled in and the subset that actually registered, which is the difference
 * that explains a bot going silent.
 */
public class MessagingDevUIJsonRPCService {

    @Inject
    ProviderRegistry registry;

    @Inject
    Instance<MessagingProvider> allProviders;

    @Inject
    MessagingConfig config;

    public List<Map<String, Object>> getProviders() {
        List<String> activeIds = registry.activeProviders().stream()
                .map(MessagingProvider::providerId)
                .toList();

        List<Map<String, Object>> providers = new ArrayList<>();
        for (MessagingProvider provider : allProviders) {
            String id = provider.providerId();
            MessagingConfig.ProviderConfig providerConfig = config.provider().get(id);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("providerId", id);
            row.put("adapter", provider.getClass().getSimpleName());
            row.put("format", String.valueOf(provider.supportedFormat()));
            row.put("configured", providerConfig != null);
            row.put("active", activeIds.contains(id));
            providers.add(row);
        }
        providers.sort((a, b) -> String.valueOf(a.get("providerId"))
                .compareTo(String.valueOf(b.get("providerId"))));
        return providers;
    }

    /** Sends a message through a provider, so an adapter's delivery path can be checked end to end. */
    public String sendTestMessage(String providerId, String chatId, String text) {
        MessagingProvider provider = registry.getProvider(providerId)
                .orElseThrow(() -> new IllegalArgumentException("No active provider: " + providerId));
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException("A chat id is required");
        }
        provider.send(chatId, text == null || text.isBlank() ? "Ping from the Qlawkus Dev UI" : text)
                .await().indefinitely();
        return "Sent via " + providerId;
    }
}
