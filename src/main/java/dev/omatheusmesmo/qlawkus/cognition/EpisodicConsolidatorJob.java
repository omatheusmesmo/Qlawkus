package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@ApplicationScoped
public class EpisodicConsolidatorJob {

  @Inject
  ChatModel chatModel;

  @Inject
  EmbeddingModel embeddingModel;

  @Inject
  EmbeddingStore<TextSegment> embeddingStore;

  @Scheduled(cron = "{qlawkus.consolidator.cron:0 0 3 * * ?}")
  void consolidate() {
    LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
    consolidateDate(yesterday);
  }

  @Transactional
  void consolidateDate(LocalDate date) {
    if (Journal.existsForDate(date)) {
      Log.debugf("Journal already exists for %s, skipping", date);
      return;
    }

    List<ChatMessageEntity> entities = ChatMessageEntity.findByDateRange(date);
    if (entities.isEmpty()) {
      Log.debugf("No messages found for %s, skipping", date);
      return;
    }

    String summary = summarizeMessages(entities, date);

    Journal journal = new Journal();
    journal.date = date;
    journal.summary = summary;
    journal.messageCount = entities.size();
    journal.persist();

    embedSummary(journal);

    Log.infof("Consolidated %d messages into journal for %s", entities.size(), date);
  }

  void embedSummary(Journal journal) {
    try {
      Metadata metadata = new Metadata();
      metadata.put("source", "episodic-consolidator");
      metadata.put("date", journal.date.toString());
      TextSegment segment = TextSegment.from(journal.summary, metadata);
      Embedding embedding = embeddingModel.embed(journal.summary).content();
      embeddingStore.add(embedding, segment);
      Log.infof("Embedded journal summary for %s", journal.date);
    } catch (Exception e) {
      Log.warnf(e, "Failed to embed journal summary for %s", journal.date);
    }
  }

  String summarizeMessages(List<ChatMessageEntity> entities, LocalDate date) {
    StringBuilder conversation = new StringBuilder();
    for (ChatMessageEntity entity : entities) {
      ChatMessage message = entity.toChatMessage();
      conversation.append(message.type().name()).append(": ").append(message).append("\n");
    }

    String prompt = """
        Summarize this day's conversation into a concise journal entry.
        Focus on: key topics discussed, decisions made, user preferences revealed, and notable interactions.
        Write in third person, past tense. Be factual and brief.

        Date: %s
        Messages (%d):
        %s""".formatted(date, entities.size(), conversation);

    try {
      return chatModel.chat(prompt);
    } catch (Exception e) {
      Log.warnf(e, "Failed to summarize messages for %s", date);
      return "Consolidation failed for " + date + ": " + e.getMessage();
    }
  }
}
