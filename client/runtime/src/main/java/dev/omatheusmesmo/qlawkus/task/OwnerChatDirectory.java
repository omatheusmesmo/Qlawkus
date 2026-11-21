package dev.omatheusmesmo.qlawkus.task;

import java.util.Optional;

public interface OwnerChatDirectory {

    void remember(String providerId, String chatId);

    Optional<OwnerChat> lastChat(String providerId);

    Optional<OwnerChat> mostRecent();
}
