package dev.omatheusmesmo.qlawkus.cognition;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;

@ApplicationScoped
public class JournalPersistHelper {

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public Journal persist(LocalDate date, String summary, int messageCount) {
    if (Journal.existsForDate(date)) return null;
    Journal journal = new Journal();
    journal.date = date;
    journal.summary = summary;
    journal.messageCount = messageCount;
    journal.persist();
    return journal;
  }
}
