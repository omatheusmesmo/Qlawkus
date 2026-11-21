package dev.omatheusmesmo.qlawkus.store;

import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import java.util.List;

public interface OwnerChatStore {

    void save(OwnerChat chat);

    List<OwnerChat> loadAll();
}
