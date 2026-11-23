package dev.omatheusmesmo.qlawkus.task;

import java.util.List;
import java.util.Optional;

public interface OwnerChatDirectory {

    void remember(String providerId, String chatId);

    Optional<OwnerChat> lastChat(String providerId);

    Optional<OwnerChat> mostRecent();

    default List<OwnerChat> knownChats() {
        return mostRecent().stream().toList();
    }
}
