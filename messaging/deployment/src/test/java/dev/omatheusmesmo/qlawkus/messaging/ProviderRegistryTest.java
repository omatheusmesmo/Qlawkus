package dev.omatheusmesmo.qlawkus.messaging;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderRegistryTest {

    private ProviderRegistry registry;
    private MessagingConfig config;

    private MessagingProvider provider(String id) {
        MessagingProvider p = Mockito.mock(MessagingProvider.class);
        when(p.providerId()).thenReturn(id);
        when(p.supportedFormat()).thenReturn(MessagingFormat.PLAIN_TEXT);
        when(p.receive(any())).thenReturn(Uni.createFrom().item(new MessagingResponse(id, "ok")));
        when(p.send(any(), any())).thenReturn(Uni.createFrom().voidItem());
        return p;
    }

    private MessagingConfig configWith(Map<String, Boolean> enabled) {
        MessagingConfig cfg = Mockito.mock(MessagingConfig.class);
        Map<String, MessagingConfig.ProviderConfig> providerMap = new java.util.HashMap<>();
        enabled.forEach((id, flag) -> {
            MessagingConfig.ProviderConfig pc = Mockito.mock(MessagingConfig.ProviderConfig.class);
            when(pc.enabled()).thenReturn(flag);
            providerMap.put(id, pc);
        });
        when(cfg.provider()).thenReturn(providerMap);
        return cfg;
    }

    @SuppressWarnings("unchecked")
    private Instance<MessagingProvider> instanceOf(MessagingProvider... providers) {
        Instance<MessagingProvider> inst = Mockito.mock(Instance.class);
        when(inst.iterator()).thenReturn(List.of(providers).iterator());
        return inst;
    }

    @BeforeEach
    void setUp() {
        registry = new ProviderRegistry();
    }

    @Test
    void init_registersAllEnabledProviders() {
        MessagingProvider telegram = provider("telegram");
        MessagingProvider discord = provider("discord");
        registry.providers = instanceOf(telegram, discord);
        registry.config = configWith(Map.of("telegram", true, "discord", true));

        registry.init();

        assertEquals(2, registry.activeProviders().size());
    }

    @Test
    void init_skipsDisabledProvider() {
        MessagingProvider telegram = provider("telegram");
        MessagingProvider discord = provider("discord");
        registry.providers = instanceOf(telegram, discord);
        registry.config = configWith(Map.of("telegram", true, "discord", false));

        registry.init();

        assertEquals(1, registry.activeProviders().size());
        assertTrue(registry.getProvider("telegram").isPresent());
        assertTrue(registry.getProvider("discord").isEmpty());
    }

    @Test
    void init_noConfigEntry_enablesByDefault() {
        MessagingProvider telegram = provider("telegram");
        registry.providers = instanceOf(telegram);
        registry.config = configWith(Map.of());

        registry.init();

        assertTrue(registry.getProvider("telegram").isPresent());
    }

    @Test
    void getProvider_unknownId_returnsEmpty() {
        registry.providers = instanceOf();
        registry.config = configWith(Map.of());
        registry.init();

        assertTrue(registry.getProvider("unknown").isEmpty());
    }

    @Test
    void activeProviders_isUnmodifiable() {
        registry.providers = instanceOf();
        registry.config = configWith(Map.of());
        registry.init();

        assertThrows(UnsupportedOperationException.class,
                () -> registry.activeProviders().add(provider("x")));
    }
}
