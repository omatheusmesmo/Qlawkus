package dev.omatheusmesmo.qlawkus.messaging;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ProviderRegistry {

    @Inject
    Instance<MessagingProvider> providers;

    @Inject
    MessagingConfig config;

    private final Map<String, MessagingProvider> active = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        for (MessagingProvider provider : providers) {
            String id = provider.providerId();
            MessagingConfig.ProviderConfig providerConfig = config.provider().get(id);
            boolean enabled = providerConfig == null || providerConfig.enabled();
            if (enabled) {
                active.put(id, provider);
                Log.infof("ProviderRegistry: registered provider=%s class=%s", id, provider.getClass().getSimpleName());
            } else {
                Log.infof("ProviderRegistry: provider=%s disabled by config", id);
            }
        }
        Log.infof("ProviderRegistry: %d active provider(s): %s", active.size(), active.keySet());
    }

    public Optional<MessagingProvider> getProvider(String providerId) {
        return Optional.ofNullable(active.get(providerId));
    }

    public Collection<MessagingProvider> activeProviders() {
        return Collections.unmodifiableCollection(active.values());
    }
}
