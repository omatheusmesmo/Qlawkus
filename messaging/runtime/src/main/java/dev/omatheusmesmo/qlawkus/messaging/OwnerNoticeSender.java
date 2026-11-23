package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.messaging.format.FormatterRegistry;
import dev.omatheusmesmo.qlawkus.task.OwnerNoticeDelivery;
import dev.omatheusmesmo.qlawkus.task.TaskDestination;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;

@ApplicationScoped
class OwnerNoticeSender implements OwnerNoticeDelivery {

    private final ProviderRegistry providers;
    private final FormatterRegistry formatters;

    @Inject
    OwnerNoticeSender(ProviderRegistry providers, FormatterRegistry formatters) {
        this.providers = providers;
        this.formatters = formatters;
    }

    @Override
    public boolean isAvailable(String provider) {
        return providers.isAvailable(provider);
    }

    @Override
    public void send(TaskDestination destination, String notice, Duration remaining) {
        MessagingProvider provider = providers.getProvider(destination.providerId()).orElseThrow();
        String formatted = formatters.forFormat(provider.supportedFormat()).format(notice);
        provider.send(destination.chatId(), formatted).await().atMost(remaining);
    }
}
