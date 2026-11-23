package dev.omatheusmesmo.qlawkus.messaging.telegram;

import dev.omatheusmesmo.qlawkus.attachment.AttachmentRejected;
import dev.omatheusmesmo.qlawkus.attachment.ChatAttachment;
import dev.omatheusmesmo.qlawkus.messaging.MediaDownloader;
import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.MessagingOrchestrator;
import dev.omatheusmesmo.qlawkus.messaging.MessagingProvider;
import dev.omatheusmesmo.qlawkus.messaging.progress.ToolProgressSink;
import dev.omatheusmesmo.qlawkus.messaging.MessagingResponse;
import dev.omatheusmesmo.qlawkus.messaging.approval.ApprovalCallbacks;
import dev.omatheusmesmo.qlawkus.messaging.auth.MessagingAuthService;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.Cancellable;
import io.vertx.core.buffer.Buffer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.client.api.ClientMultipartForm;

import java.net.URI;
import java.time.Duration;
import java.util.List;
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

    @Inject
    MessagingAuthService authService;

    @Inject
    MediaDownloader mediaDownloader;

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
        Cancellable typing = startTypingIndicator(message.chatId());
        return orchestrator.process(message)
                .onTermination().invoke(typing::cancel)
                .replaceWith(new MessagingResponse(message.chatId(), ""));
    }

    private Cancellable startTypingIndicator(String chatId) {
        Optional<String> token = botToken();
        if (token.isEmpty()) {
            return () -> { };
        }
        sendChatAction(token.get(), chatId);
        return Multi.createFrom().ticks().every(Duration.ofSeconds(4))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem().invoke(tick -> sendChatAction(token.get(), chatId))
                .subscribe().with(ignored -> { }, error -> { });
    }

    private void sendChatAction(String token, String chatId) {
        try {
            botClient.sendChatAction(token, new TelegramBotClient.SendChatActionRequest(chatId, "typing"));
        } catch (Exception e) {
            Log.debugf("Telegram: sendChatAction failed chatId=%s: %s", chatId, e.getMessage());
        }
    }

    @Override
    public Uni<Void> send(String chatId, String text) {
        Optional<String> token = botToken();
        if (token.isEmpty()) {
            return Uni.createFrom().failure(new IllegalStateException("Telegram bot-token not configured"));
        }
        return Uni.createFrom()
                .item(() -> {
                    botClient.sendMessage(
                            token.get(),
                            new TelegramBotClient.SendMessageRequest(chatId, text, "MarkdownV2"));
                    return null;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .replaceWithVoid();
    }

    @Override
    public Uni<Void> sendFile(String chatId, String filename, String mediaType, byte[] bytes) {
        Optional<String> token = botToken();
        if (token.isEmpty()) {
            return Uni.createFrom().failure(new IllegalStateException("Telegram bot-token not configured"));
        }
        var form = ClientMultipartForm.create()
                .attribute("chat_id", chatId, null)
                .binaryFileUpload("document", filename, Buffer.buffer(bytes), mediaType);
        return botClient.sendDocument(token.get(), form)
                .onItem().invoke(response -> {
                    if (response == null || !response.ok() || response.result() == null) {
                        throw new IllegalStateException("Telegram did not acknowledge the document");
                    }
                }).replaceWithVoid();
    }

    @Override
    public Uni<Void> sendVoice(String chatId, byte[] audio, String filename, String fallbackText) {
        Optional<String> token = botToken();
        if (token.isEmpty()) {
            return send(chatId, fallbackText);
        }
        var form = ClientMultipartForm.create()
                .attribute("chat_id", chatId, null)
                .binaryFileUpload("audio", filename, Buffer.buffer(audio), "audio/mpeg");
        return botClient.sendAudio(token.get(), form)
                .onItem().invoke(response -> {
                    if (response == null || !response.ok() || response.result() == null) {
                        throw new IllegalStateException("Telegram did not acknowledge the audio message");
                    }
                })
                .replaceWithVoid();
    }

    @Override
    public Uni<Boolean> sendApprovalPrompt(String chatId, String requestId, String text) {
        Optional<String> token = botToken();
        if (token.isEmpty()) {
            return Uni.createFrom().item(false);
        }
        var buttons = new TelegramBotClient.InlineKeyboardMarkup(List.of(List.of(
                new TelegramBotClient.InlineKeyboardButton("Approve", ApprovalCallbacks.approve(requestId)),
                new TelegramBotClient.InlineKeyboardButton("Deny", ApprovalCallbacks.deny(requestId)))));
        return Uni.createFrom()
                .item(() -> {
                    botClient.sendMessage(token.get(),
                            new TelegramBotClient.SendMessageRequest(chatId, text, null, buttons));
                    return true;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    /**
     * Handles a pressed inline button. The press is turned into the typed {@code /approve} or {@code /deny}
     * command from the user who pressed it and handed to the orchestrator, which applies the allowlist, so a
     * press by anyone but the owner decides nothing and leaves the buttons in place.
     */
    public Uni<Void> handleCallback(TelegramUpdate update) {
        TelegramUpdate.TelegramCallbackQuery press = update.callbackQuery();
        Optional<String> token = botToken();
        if (press == null || token.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        TelegramUpdate.TelegramMessage origin = press.message();
        Optional<MessagingMessage> command = origin == null || origin.chat() == null || press.from() == null
                ? Optional.empty()
                : ApprovalCallbacks.toCommand("telegram", String.valueOf(origin.chat().id()),
                        String.valueOf(press.from().id()), press.data());
        if (command.isEmpty()) {
            return Uni.createFrom().item(() -> acknowledge(token.get(), press.id()))
                    .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()).replaceWithVoid();
        }
        MessagingMessage message = command.get();
        if (!authService.isAuthorized(message)) {
            Log.warnf("Telegram: approval button press rejected userId=%s reason=allowlist", message.userId());
            return Uni.createFrom().item(() -> acknowledge(token.get(), press.id()))
                    .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()).replaceWithVoid();
        }
        return orchestrator.process(message)
                .onItem().invoke(ignored -> removeButtons(token.get(), message.chatId(), origin.messageId()))
                .onFailure().invoke(failure -> Log.errorf(failure, "Telegram: approval button press failed userId=%s",
                        message.userId()))
                .onFailure().recoverWithNull()
                .invoke(() -> acknowledge(token.get(), press.id()))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .replaceWithVoid();
    }

    private Object acknowledge(String token, String callbackQueryId) {
        try {
            botClient.answerCallbackQuery(token, new TelegramBotClient.AnswerCallbackQueryRequest(callbackQueryId, null));
        } catch (RuntimeException failure) {
            Log.debugf("Telegram: answerCallbackQuery failed: %s", failure.getMessage());
        }
        return null;
    }

    private void removeButtons(String token, String chatId, long messageId) {
        try {
            botClient.editMessageReplyMarkup(token, new TelegramBotClient.EditMessageReplyMarkupRequest(chatId,
                    messageId, new TelegramBotClient.InlineKeyboardMarkup(List.of())));
        } catch (RuntimeException failure) {
            Log.debugf("Telegram: removing approval buttons failed: %s", failure.getMessage());
        }
    }

    @Override
    public Optional<ToolProgressSink> progressSink() {
        return botToken().map(token -> new TelegramProgressSink(botClient, token));
    }

    private Optional<String> botToken() {
        return config.botToken().filter(token -> !token.isBlank());
    }

    private record TelegramProgressSink(TelegramBotClient botClient, String token) implements ToolProgressSink {

        @Override
        public String post(String chatId, String text) {
            TelegramBotClient.SendMessageResponse response =
                    botClient.sendMessage(token, new TelegramBotClient.SendMessageRequest(chatId, text, null));
            if (response == null || response.result() == null) {
                throw new IllegalStateException("Telegram sendMessage returned no message");
            }
            return String.valueOf(response.result().messageId());
        }

        @Override
        public void edit(String chatId, String messageId, String text) {
            botClient.editMessageText(token,
                    new TelegramBotClient.EditMessageTextRequest(chatId, Long.parseLong(messageId), text));
        }

        @Override
        public void delete(String chatId, String messageId) {
            botClient.deleteMessage(token,
                    new TelegramBotClient.DeleteMessageRequest(chatId, Long.parseLong(messageId)));
        }
    }

    public MessagingMessage mapUpdate(TelegramUpdate update) {
        TelegramUpdate.TelegramMessage msg = update.message();
        String userId = String.valueOf(msg.from().id());
        String chatId = String.valueOf(msg.chat().id());
        String text = msg.text() != null ? msg.text() : "";
        var attachments = mapAttachments(msg);
        if (!attachments.isEmpty() && msg.caption() != null) text = msg.caption();
        return new MessagingMessage("telegram", chatId, userId, text, extractVoice(msg), attachments);
    }

    private List<ChatAttachment> mapAttachments(TelegramUpdate.TelegramMessage message) {
        if (message.document() != null) {
            var document = message.document();
            return List.of(new ChatAttachment(document.mimeType(), document.size(), () -> fetchAttachment(document.fileId())));
        }
        if (message.photo() == null || message.photo().isEmpty()) return List.of();
        // Telegram lists the available renditions in size order. Keep the largest that could fit.
        for (var photo : message.photo().reversed()) {
            if (photo.size() == null || photo.size() <= ChatAttachment.MAX_BYTES) {
                return List.of(new ChatAttachment("image/jpeg", photo.size(), () -> fetchAttachment(photo.fileId())));
            }
        }
        var photo = message.photo().getFirst();
        return List.of(new ChatAttachment("image/jpeg", photo.size(), () -> fetchAttachment(photo.fileId())));
    }

    private byte[] fetchAttachment(String fileId) {
        String token = botToken().orElseThrow(() -> new AttachmentRejected("Telegram attachment download is unavailable."));
        if (fileId == null || !fileId.matches("[a-zA-Z0-9_-]{1,512}")) {
            throw new AttachmentRejected("Invalid Telegram attachment ID.");
        }
        TelegramBotClient.GetFileResponse file;
        try {
            file = botClient.getFile(token, fileId);
        } catch (RuntimeException unavailable) {
            throw new AttachmentRejected("Could not locate the Telegram attachment.");
        }
        String path = file == null || file.result() == null ? null : file.result().filePath();
        if (path == null || !path.matches("[a-zA-Z0-9_-][a-zA-Z0-9_./-]*") || path.contains("..")) {
            throw new AttachmentRejected("Telegram returned an invalid attachment path.");
        }
        return mediaDownloader.downloadAttachment(URI.create(config.apiBaseUrl() + "/file/bot" + token + "/" + path));
    }

    private Optional<byte[]> extractVoice(TelegramUpdate.TelegramMessage msg) {
        if (msg.voice() == null) {
            return Optional.empty();
        }
        Optional<String> token = botToken();
        if (token.isEmpty()) {
            Log.warnf("Telegram: voice note received but bot-token not configured");
            return Optional.empty();
        }
        try {
            TelegramBotClient.GetFileResponse file = botClient.getFile(token.get(), msg.voice().fileId());
            if (file == null || file.result() == null || file.result().filePath() == null) {
                Log.warnf("Telegram: getFile returned no file_path for voice note");
                return Optional.empty();
            }
            String url = config.apiBaseUrl() + "/file/bot" + token.get() + "/" + file.result().filePath();
            return Optional.of(mediaDownloader.download(url));
        } catch (Exception e) {
            Log.errorf("Telegram: failed to fetch voice note exception=%s", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
