package dev.omatheusmesmo.qlawkus.store.pg;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * The Postgres answer to {@code verifyWritable}: asks the server whether this session could write to
 * a store's table, without writing. Readiness polls it every few seconds, so a real insert would
 * fill the table, and even a zero-row one takes a {@code RowExclusiveLock}, fires statement-level
 * triggers, and needs a column list kept in step with the schema ({@code SELECT *} is rejected on
 * {@code embeddings}, whose {@code content_hash} is a generated column).
 *
 * <p>It catches what every {@code load()} passes: a read-only session (a hot standby, or a database
 * set to {@code default_transaction_read_only}) and a role that can read the table but not change
 * it. A table that does not exist fails here too, which keeps the names the stores pass honest. It
 * cannot see a full disk, which only an actual write would reveal.
 */
@ApplicationScoped
public class PgWriteProbe {

  /**
   * The tables the stores write, each with exactly the DML its store issues. Demanding more than
   * that would take a correctly least-privileged instance out of readiness, which on a single
   * instance means nobody answers. The pgvector upsert is {@code ON CONFLICT DO UPDATE}, which is why
   * {@code embeddings} needs UPDATE; the persona and the profile are seeded rows only ever updated.
   */
  public enum Table {
    SOUL("soul", "UPDATE"),
    USER_PROFILE("user_profile", "UPDATE"),
    EMBEDDINGS("embeddings", "INSERT", "UPDATE", "DELETE"),
    JOURNAL("journal", "INSERT", "DELETE"),
    CHAT_MESSAGES("chat_message_entity", "INSERT", "DELETE"),
    SKILL("skill", "INSERT", "UPDATE", "DELETE");

    final String name;
    final List<String> privileges;

    Table(String name, String... privileges) {
      this.name = name;
      this.privileges = List.of(privileges);
    }
  }

  @Inject
  DataSource dataSource;

  public void verify(Table table) {
    try (Connection connection = dataSource.getConnection()) {
      verify(connection, table);
    } catch (SQLException e) {
      throw new IllegalStateException("Cannot reach the database to probe table " + table.name, e);
    }
  }

  public static void verify(Connection connection, Table table) {
    StringBuilder question = new StringBuilder("SELECT current_setting('transaction_read_only')");
    table.privileges.forEach(privilege -> question.append(", has_table_privilege(?, ?)"));
    List<String> refused = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(question.toString())) {
      int parameter = 1;
      for (String privilege : table.privileges) {
        statement.setString(parameter++, table.name);
        statement.setString(parameter++, privilege);
      }
      try (ResultSet answer = statement.executeQuery()) {
        answer.next();
        if ("on".equals(answer.getString(1))) {
          refused.add("the session is read-only");
        }
        for (int i = 0; i < table.privileges.size(); i++) {
          if (!answer.getBoolean(i + 2)) {
            refused.add("no " + table.privileges.get(i) + " privilege");
          }
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Cannot probe table " + table.name + ": " + e.getMessage(), e);
    }
    if (!refused.isEmpty()) {
      throw new IllegalStateException("Cannot write to table " + table.name + ": "
          + String.join(", ", refused));
    }
  }
}
