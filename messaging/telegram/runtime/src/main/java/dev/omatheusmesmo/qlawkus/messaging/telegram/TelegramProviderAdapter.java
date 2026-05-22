package dev.omatheusmesmo.qlawkus.messaging.telegram;

import dev.omatheusmesmo.qlawkus.messaging.MediaDownloader;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

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
        return orchestrator.process(message)
                .replaceWith(new MessagingResponse(message.chatId(), ""));
    }

    @Override
    public Uni<Void> send(String chatId, String text) {
        Optional<String> token = botToken();
        if (token.isEmpty()) {
            Log.warnf("Telegram: bot-token not configured, dropping message to chatId=%s", chatId);
            return Uni.createFrom().voidItem();
        }
        return Uni.createFrom()
                .item(() -> {
                    botClient.sendMessage(
                            token.get(),
                            new TelegramBotClient.SendMessageRequest(chatId, text, "MarkdownV2"));
                    return null;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onFailure().invoke(err -> Log.errorf(err, "Telegram send failed chatId=%s", chatId))
                .onFailure().recoverWithNull()
                .replaceWithVoid();
    }

    @Override
    public Uni<Void> sendVoice(String chatId, byte[] audio, String filename, String fallbackText) {
        if (botToken().isEmpty()) {
            return send(chatId, fallbackText);
        }
        return Uni.createFrom()
                .item(() -> {
                    sendAudioMultipart(chatId, audio, filename);
                    return (Void) null;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onFailure().invoke(err -> Log.errorf(err,
                        "Telegram: sendVoice failed chatId=%s, falling back to text", chatId))
                .onFailure().recoverWithUni(() -> send(chatId, fallbackText))
                .replaceWithVoid();
    }

    private void sendAudioMultipart(String chatId, byte[] audio, String filename) {
        String boundary = "----TelegramBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildAudioMultipart(boundary, chatId, audio, filename);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.apiBaseUrl() + "/bot" + botToken().orElseThrow() + "/sendAudio"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofSeconds(60))
                .build();

        try {
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Telegram sendAudio error %d: %s".formatted(response.statusCode(), response.body()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Telegram sendAudio interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to call Telegram sendAudio", e);
        }
    }

    private byte[] buildAudioMultipart(String boundary, String chatId, byte[] audio, String filename) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n"
                    + chatId + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"audio\"; filename=\"" + filename + "\"\r\n"
                    + "Content-Type: audio/mpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(audio);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to build Telegram audio multipart body", e);
        }
        return out.toByteArray();
    }

    private Optional<String> botToken() {
        return config.botToken().filter(token -> !token.isBlank());
    }

    public MessagingMessage mapUpdate(TelegramUpdate update) {
        TelegramUpdate.TelegramMessage msg = update.message();
        String userId = String.valueOf(msg.from().id());
        String chatId = String.valueOf(msg.chat().id());
        String text = msg.text() != null ? msg.text() : "";
        return new MessagingMessage("telegram", chatId, userId, text, extractVoice(msg));
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
            Log.errorf("Telegram: failed to fetch voice note: %s", e.getMessage());
            return Optional.empty();
        }
    }
}
