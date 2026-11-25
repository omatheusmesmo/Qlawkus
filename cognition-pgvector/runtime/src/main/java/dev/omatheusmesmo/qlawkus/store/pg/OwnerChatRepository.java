package dev.omatheusmesmo.qlawkus.store.pg;

import jakarta.enterprise.inject.Instance;
import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/** Stores one observed destination per provider. PostgreSQL compares timestamps atomically. */
@ApplicationScoped
public class OwnerChatRepository {

  private static final String UPSERT = """
      INSERT INTO owner_chat (provider_id, chat_id, last_seen)
      VALUES (?, ?, ?)
      ON CONFLICT (provider_id) DO UPDATE
      SET chat_id = EXCLUDED.chat_id, last_seen = EXCLUDED.last_seen
      WHERE owner_chat.last_seen < EXCLUDED.last_seen
         OR (owner_chat.last_seen = EXCLUDED.last_seen AND ?)
      """;

  private final Instance<DataSource> dataSources;

  @Inject
  public OwnerChatRepository(Instance<DataSource> dataSources) {
    this.dataSources = dataSources;
  }

  void save(OwnerChat chat, boolean replaceAtSameTime) {
    try (var connection = dataSources.get().getConnection();
        var statement = connection.prepareStatement(UPSERT)) {
      statement.setString(1, chat.providerId());
      statement.setString(2, chat.chatId());
      statement.setObject(3, chat.lastSeen().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC));
      statement.setBoolean(4, replaceAtSameTime);
      statement.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("Cannot save the owner's last chat in PostgreSQL", e);
    }
  }

  List<OwnerChat> listAll() {
    try (var connection = dataSources.get().getConnection();
        var statement = connection.prepareStatement(
            "SELECT provider_id, chat_id, last_seen FROM owner_chat ORDER BY provider_id");
        var rows = statement.executeQuery()) {
      var chats = new ArrayList<OwnerChat>();
      while (rows.next()) {
        chats.add(new OwnerChat(rows.getString(1), rows.getString(2),
            rows.getObject(3, OffsetDateTime.class).toInstant()));
      }
      return List.copyOf(chats);
    } catch (SQLException e) {
      throw new IllegalStateException("Cannot load the owner's last chats from PostgreSQL", e);
    }
  }
}
