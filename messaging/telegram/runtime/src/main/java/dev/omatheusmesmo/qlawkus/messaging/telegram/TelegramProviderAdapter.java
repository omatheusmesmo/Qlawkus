package dev.omatheusmesmo.qlawkus.messaging.telegram;

import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.MessagingOrchestrator;
import dev.omatheusmesmo.qlawkus.messaging.MessagingProvider;
import dev.omatheusmesmo.qlawkus.messaging.MessagingResponse;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Optional;

@ApplicationScoped
public class TelegramProviderAdapter implements MessagingProvider {

    @Inject
    TelegramConfig config;

    @Inject
    @RestClient
    TelegramBotClient botClient;

    @Inject
    MessagingOrchestrator orchestrator;

    @Override
    public String providerId() {
        return "telegram";
    }

    @Override
    public MessagingFormat supportedFormat() {
        return MessagingFormat.TELEGRAM_MARKDOWN_V2;
    }

    @Override
    public Uni<MessagingResponse> receive(MessagingMessage message) {
        return orchestrator.process(message)
                .replaceWith(new MessagingResponse(message.chatId(), ""));
    }

    @Override
    public Uni<Void> send(String chatId, String text) {
        return Uni.createFrom()
                .item(() -> {
                    botClient.sendMessage(
                            config.botToken(),
                            new TelegramBotClient.SendMessageRequest(chatId, text, "MarkdownV2"));
                    return null;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onFailure().invoke(err -> Log.errorf(err, "Telegram send failed chatId=%s", chatId))
                .onFailure().recoverWithNull()
                .replaceWithVoid();
    }

    public MessagingMessage mapUpdate(TelegramUpdate update) {
        TelegramUpdate.TelegramMessage msg = update.message();
        String userId = String.valueOf(msg.from().id());
        String chatId = String.valueOf(msg.chat().id());
        String text = msg.text() != null ? msg.text() : "";
        Optional<byte[]> audio = Optional.empty();
        return new MessagingMessage("telegram", chatId, userId, text, audio);
    }
}
