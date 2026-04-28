package dev.omatheusmesmo.qlawkus.store;

import dev.langchain4j.data.message.ChatMessage;
import java.time.LocalDate;
import java.util.List;

public interface WorkingMemoryStore {

  List<ChatMessage> getMessages(String memoryId);

  void updateMessages(String memoryId, List<ChatMessage> messages);

  void deleteMessages(String memoryId);

  List<ChatMessage> findByDateRange(LocalDate date);

  long count();

  void purgeAll();
}
