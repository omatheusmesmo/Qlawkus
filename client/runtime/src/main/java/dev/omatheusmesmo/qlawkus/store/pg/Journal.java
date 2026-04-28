package dev.omatheusmesmo.qlawkus.store.pg;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Entity
public class Journal extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, unique = true)
  public LocalDate date;

  @Column(columnDefinition = "TEXT", nullable = false)
  public String summary;

  @Column(nullable = false)
  public int messageCount;

  public Instant createdAt;

  public Instant updatedAt;

  public static Journal findByDate(LocalDate date) {
    return find("date = ?1", date).firstResult();
  }

  public static boolean existsForDate(LocalDate date) {
    return count("date = ?1", date) > 0;
  }

  public static List<Journal> findByDateRange(LocalDate start, LocalDate end) {
    return list("date >= ?1 and date <= ?2 order by date", start, end);
  }

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
