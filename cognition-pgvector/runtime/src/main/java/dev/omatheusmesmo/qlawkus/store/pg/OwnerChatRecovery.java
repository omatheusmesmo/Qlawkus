package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownOwnerChatStore;
import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import io.quarkus.logging.Log;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

/** Recovers the newest available observation per provider and repairs stale copies. */
final class OwnerChatRecovery {

  private OwnerChatRecovery() {
  }

  static List<OwnerChat> load(MarkdownOwnerChatStore files, OwnerChatRepository database, boolean mirrorFiles) {
    var fileRead = read("files", files::loadAll);
    var databaseRead = read("PostgreSQL", database::listAll);
    Map<String, OwnerChat> fileChats = fileRead.orElseGet(Map::of);
    Map<String, OwnerChat> databaseChats = databaseRead.orElseGet(Map::of);
    var providers = new TreeSet<>(fileChats.keySet());
    providers.addAll(databaseChats.keySet());
    var latest = new ArrayList<OwnerChat>();
    for (String provider : providers) {
      OwnerChat file = fileChats.get(provider);
      OwnerChat stored = databaseChats.get(provider);
      if (stored == null || (file != null
          && file.lastSeen().truncatedTo(ChronoUnit.MICROS).isAfter(stored.lastSeen()))) {
        latest.add(file);
        if (databaseRead.isPresent()) {
          repair("PostgreSQL", () -> database.save(file, false));
        }
      } else {
        latest.add(stored);
        if (mirrorFiles && fileRead.isPresent() && (file == null || stored.lastSeen().isAfter(file.lastSeen()))) {
          repair("files", () -> files.save(stored));
        }
      }
    }
    return List.copyOf(latest);
  }

  private static Optional<Map<String, OwnerChat>> read(String backend, Supplier<List<OwnerChat>> source) {
    try {
      Map<String, OwnerChat> chats = new TreeMap<>();
      source.get().forEach(chat -> chats.put(chat.providerId(), chat));
      return Optional.of(chats);
    } catch (RuntimeException failure) {
      Log.warnf("Could not read owner chats from %s: error=%s", backend, failure.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  private static void repair(String backend, Runnable persist) {
    try {
      persist.run();
    } catch (RuntimeException failure) {
      Log.warnf("Could not recover owner chats in %s: error=%s", backend, failure.getClass().getSimpleName());
    }
  }
}
