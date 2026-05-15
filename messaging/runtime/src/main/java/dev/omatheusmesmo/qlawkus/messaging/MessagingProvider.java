package dev.omatheusmesmo.qlawkus.messaging;

import io.smallrye.mutiny.Uni;

public interface MessagingProvider {

    String providerId();

    MessagingFormat supportedFormat();

    Uni<MessagingResponse> receive(MessagingMessage message);

    Uni<Void> send(String chatId, String text);
}
