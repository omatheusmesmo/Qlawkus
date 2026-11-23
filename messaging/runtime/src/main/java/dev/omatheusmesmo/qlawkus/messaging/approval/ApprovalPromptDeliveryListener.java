package dev.omatheusmesmo.qlawkus.messaging.approval;

import dev.omatheusmesmo.qlawkus.approval.ApprovalPromptEvent;
import dev.omatheusmesmo.qlawkus.messaging.MessagingLog;
import dev.omatheusmesmo.qlawkus.messaging.MessagingProvider;
import dev.omatheusmesmo.qlawkus.messaging.NotificationService;
import dev.omatheusmesmo.qlawkus.messaging.ProviderRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

/**
 * Delivers an approval prompt to the chat the request came from: with buttons where the platform has
 * them, as plain text otherwise. A platform whose button delivery fails still gets the text, because a
 * prompt the owner never sees would leave the request to expire unnoticed.
 */
@ApplicationScoped
public class ApprovalPromptDeliveryListener {

    private final ProviderRegistry providers;
    private final NotificationService notifications;

    @Inject
    public ApprovalPromptDeliveryListener(ProviderRegistry providers, NotificationService notifications) {
        this.providers = providers;
        this.notifications = notifications;
    }

    void onPrompt(@ObservesAsync ApprovalPromptEvent event) {
        deliver(event);
    }

    void deliver(ApprovalPromptEvent event) {
        boolean withButtons = providers.getProvider(event.providerId())
                .map(provider -> offerButtons(provider, event))
                .orElse(false);
        if (!withButtons) {
            notifications.send(event.providerId(), event.chatId(), event.text(), null).await().indefinitely();
        }
    }

    private boolean offerButtons(MessagingProvider provider, ApprovalPromptEvent event) {
        try {
            Boolean delivered = provider.sendApprovalPrompt(event.chatId(), event.requestId(), event.text())
                    .await().indefinitely();
            return Boolean.TRUE.equals(delivered);
        } catch (RuntimeException failure) {
            MessagingLog.warn(event.providerId(), null,
                    "Approval prompt buttons could not be delivered provider=%s error=%s, sending text",
                    event.providerId(), MessagingLog.exceptionName(failure));
            return false;
        }
    }
}
