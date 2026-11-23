package dev.omatheusmesmo.qlawkus.messaging.approval;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.approval.ApprovalPromptEvent;
import dev.omatheusmesmo.qlawkus.messaging.MessagingProvider;
import dev.omatheusmesmo.qlawkus.messaging.NotificationService;
import dev.omatheusmesmo.qlawkus.messaging.ProviderRegistry;
import io.smallrye.mutiny.Uni;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApprovalPromptDeliveryListenerTest {

    private static final String ID = "0123456789abcdef0123456789abcdef";
    private static final ApprovalPromptEvent EVENT = new ApprovalPromptEvent("telegram", "42", ID, "Approval needed");

    private ProviderRegistry providers;
    private NotificationService notifications;
    private MessagingProvider provider;
    private ApprovalPromptDeliveryListener listener;

    @BeforeEach
    void setUp() {
        providers = mock(ProviderRegistry.class);
        notifications = mock(NotificationService.class);
        provider = mock(MessagingProvider.class);
        when(providers.getProvider("telegram")).thenReturn(Optional.of(provider));
        when(notifications.send(anyString(), anyString(), anyString(), any()))
                .thenReturn(Uni.createFrom().voidItem());
        listener = new ApprovalPromptDeliveryListener(providers, notifications);
    }

    @Test
    void aPlatformWithButtonsGetsThePromptWithButtonsAndNoDuplicateText() {
        when(provider.sendApprovalPrompt("42", ID, "Approval needed")).thenReturn(Uni.createFrom().item(true));

        listener.deliver(EVENT);

        verify(provider).sendApprovalPrompt("42", ID, "Approval needed");
        verify(notifications, never()).send(anyString(), anyString(), anyString(), any());
    }

    @Test
    void aPlatformWithoutButtonsGetsTheTextThatNamesTheCommands() {
        when(provider.sendApprovalPrompt("42", ID, "Approval needed")).thenReturn(Uni.createFrom().item(false));

        listener.deliver(EVENT);

        verify(notifications).send(eq("telegram"), eq("42"), eq("Approval needed"), isNull());
    }

    @Test
    void aFailedButtonDeliveryFallsBackToTheTextSoTheOwnerStillSeesTheRequest() {
        when(provider.sendApprovalPrompt("42", ID, "Approval needed"))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("blocked")));

        listener.deliver(EVENT);

        verify(notifications).send(eq("telegram"), eq("42"), eq("Approval needed"), isNull());
    }

    @Test
    void aProviderThatIsNotActiveDeliversThroughTheNotificationServiceWhichReportsIt() {
        when(providers.getProvider("telegram")).thenReturn(Optional.empty());

        listener.deliver(EVENT);

        verify(notifications).send(eq("telegram"), eq("42"), eq("Approval needed"), isNull());
    }
}
