package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class EpisodicConsolidatorJob {

  @Inject
  ChatModel chatModel;

  @Inject
  FactStore factStore;

  @Inject
  WorkingMemoryStore workingMemoryStore;

  @Inject
  EpisodicStore episodicStore;

  @Scheduled(cron = "{qlawkus.consolidator.cron:0 0 3 * * ?}")
  void consolidate() {
    LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
    consolidateDate(yesterday);
  }

  public void consolidateDate(LocalDate date) {
    if (episodicStore.existsForDate(date)) {
      Log.debugf("Journal already exists for %s, skipping", date);
      return;
    }

    List<ChatMessage> messages = workingMemoryStore.findByDateRange(date);
    if (messages.isEmpty()) {
      Log.debugf("No messages found for %s, skipping", date);
      return;
    }

    String summary = summarizeMessages(messages, date);
    episodicStore.storeEpisode(date, summary, messages.size());

    embedSummary(date, summary);
  }

  void embedSummary(LocalDate date, String summary) {
    try {
      factStore.store(summary, Map.of("source", "episodic-consolidator", "date", date.toString()));
    } catch (Exception e) {
      Log.warnf(e, "Failed to embed journal summary for %s", date);
    }
  }

  public String summarizeMessages(List<ChatMessage> messages, LocalDate date) {
    StringBuilder conversation = new StringBuilder();
    for (ChatMessage message : messages) {
      conversation.append(message.type().name()).append(": ").append(message).append("\n");
    }

    String prompt = """
      Summarize this day's conversation into a concise journal entry.
      Focus on: key topics discussed, decisions made, user preferences revealed, and notable interactions.
      Write in third person, past tense. Be factual and brief.

      Date: %s
      Messages (%d):
      %s""".formatted(date, messages.size(), conversation);

    try {
      return chatModel.chat(prompt);
    } catch (Exception e) {
      Log.warnf(e, "Failed to summarize messages for %s", date);
      return "Consolidation failed for " + date + ": " + e.getMessage();
    }
  }
}
