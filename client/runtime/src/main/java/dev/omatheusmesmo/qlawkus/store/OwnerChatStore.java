package dev.omatheusmesmo.qlawkus.store;

import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import java.util.List;

public interface OwnerChatStore {

    void save(OwnerChat chat);

    List<OwnerChat> loadAll();

    /** Checks whether the backend can save chats without recording a destination. */
    void verifyWritable();
}
