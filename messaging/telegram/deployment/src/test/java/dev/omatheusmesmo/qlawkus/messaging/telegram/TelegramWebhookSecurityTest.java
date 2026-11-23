package dev.omatheusmesmo.qlawkus.messaging.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.artifact.ArtifactLibrary;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.MessagingResponse;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TelegramWebhookSecurityTest {

    private static final String SECRET = "test-hook_secret";
    private static final String WEBHOOK_URL = "https://bot.example.test/api/webhook/telegram";

    private final TelegramConfig config = mock(TelegramConfig.class);
    private final TelegramProviderAdapter adapter = mock(TelegramProviderAdapter.class);
    @SuppressWarnings("unchecked")
    private final Instance<ArtifactLibrary> artifacts = mock(Instance.class);
    private final TelegramWebhookResource resource = new TelegramWebhookResource();
    private final MessagingMessage mapped = MessagingMessage.text("telegram", "42", "42", "hello");

    @BeforeEach
    void configureResource() {
        resource.config = config;
        resource.adapter = adapter;
        resource.artifacts = artifacts;
        when(adapter.mapUpdate(any())).thenReturn(mapped);
        when(adapter.receive(mapped)).thenReturn(Uni.createFrom().item(new MessagingResponse("42", "")));
    }

    @Test
    void aConfiguredSecretProtectsEveryWebhookBeforeMapping() {
        when(config.webhookSecretToken()).thenReturn(Optional.of(SECRET));

        assertEquals(403, status(null, text()));
        assertEquals(403, status("invalid", document()));
        assertEquals(403, status(null, null));
        verifyNoInteractions(adapter);

        assertEquals(200, status(SECRET, document()));
        verify(adapter, timeout(2_000)).receive(mapped);
    }

    @Test
    void unauthenticatedWebhookRejectsDocumentsAndPhotosBeforeMapping() {
        when(config.webhookSecretToken()).thenReturn(Optional.empty());

        assertEquals(403, status(null, document()));
        assertEquals(403, status(null, photo()));
        verifyNoInteractions(adapter);
    }

    @Test
    void allowsExistingTextAndVoiceWebhooksWithoutAConfiguredSecret() {
        when(config.webhookSecretToken()).thenReturn(Optional.empty());

        assertEquals(200, status(null, text()));
        assertEquals(200, status(null, voice()));
        verify(adapter, timeout(2_000).times(2)).receive(mapped);
    }

    @Test
    void artifactCapabilityRequiresAnAuthenticatedWebhookInWebhookMode() {
        when(artifacts.isResolvable()).thenReturn(true);
        when(config.mode()).thenReturn("polling");
        assertEquals(404, status(null, text()));

        when(config.mode()).thenReturn("webhook");
        when(config.webhookSecretToken()).thenReturn(Optional.empty());
        assertEquals(403, status(null, text()));

        when(config.webhookSecretToken()).thenReturn(Optional.of(SECRET));
        assertEquals(403, status("incorrect", text()));
        verifyNoInteractions(adapter);
        assertEquals(200, status(SECRET, text()));
        verify(adapter, timeout(2_000)).receive(mapped);
    }

    @Test
    void aButtonPressNeedsTheConfiguredSecretBecauseAForgedOneCouldApprove() {
        when(adapter.handleCallback(any())).thenReturn(Uni.createFrom().voidItem());
        when(config.webhookSecretToken()).thenReturn(Optional.empty());

        assertEquals(403, status(null, press()));
        verifyNoInteractions(adapter);

        when(config.webhookSecretToken()).thenReturn(Optional.of(SECRET));
        assertEquals(403, status("invalid", press()));
        verifyNoInteractions(adapter);

        assertEquals(200, status(SECRET, press()));
        verify(adapter, timeout(2_000)).handleCallback(any());
    }

    @Test
    void registrationPassesTheConfiguredSecretAndLeavesPollingAlone() {
        var bot = mock(TelegramBotClient.class);
        var registration = new TelegramWebhookRegistration();
        registration.botClient = bot;
        registration.config = config;
        var request = new TelegramBotClient.SetWebhookRequest(WEBHOOK_URL, SECRET);
        when(config.webhookSecretToken()).thenReturn(Optional.of(SECRET));
        when(config.mode()).thenReturn("polling", "webhook");
        when(config.webhookUrl()).thenReturn(Optional.of(WEBHOOK_URL));
        when(config.botToken()).thenReturn(Optional.of("test-token"));
        when(bot.setWebhook("test-token", request)).thenReturn(Uni.createFrom()
                .item(new TelegramBotClient.SetWebhookResponse(true, true)));

        registration.start(null);
        verifyNoInteractions(bot);

        registration.start(null);
        verify(bot).setWebhook("test-token", request);
        assertFalse(request.toString().contains(SECRET));
    }

    private int status(String token, TelegramUpdate update) {
        return resource.webhook(token, update).await().indefinitely().getStatus();
    }

    private static TelegramUpdate press() {
        return new TelegramUpdate(2L, null, new TelegramUpdate.TelegramCallbackQuery("cb",
                new TelegramUpdate.TelegramUser(42L, "Owner", null), null, "qa:approve:x"));
    }

    private static TelegramUpdate text() {
        return update("hello", null, null, null);
    }

    private static TelegramUpdate voice() {
        return update(null, new TelegramUpdate.TelegramVoice("voice-file", 5), null, null);
    }

    private static TelegramUpdate document() {
        return update(null, null, new TelegramUpdate.TelegramDocument("document-file", "application/pdf", 200L), null);
    }

    private static TelegramUpdate photo() {
        return update(null, null, null, List.of(new TelegramUpdate.TelegramPhoto("photo-file", 200L)));
    }

    private static TelegramUpdate update(String text, TelegramUpdate.TelegramVoice voice,
            TelegramUpdate.TelegramDocument document, List<TelegramUpdate.TelegramPhoto> photos) {
        return new TelegramUpdate(1L, new TelegramUpdate.TelegramMessage(10L,
                new TelegramUpdate.TelegramUser(42L, "Owner", null),
                new TelegramUpdate.TelegramChat(42L, "private"), text, voice, null, document, photos));
    }
}
