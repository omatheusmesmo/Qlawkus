package dev.omatheusmesmo.qlawkus.tools.brag;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;

import java.time.Instant;
import java.time.LocalDate;

@Entity
public class BragEntry extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public LocalDate date;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String achievement;

    @Column(columnDefinition = "TEXT")
    public String impact;

    @Column(length = 128)
    public String repo;

    @Column(nullable = false)
    public boolean deleted = false;

    @Column(nullable = false)
    public Instant createdAt;

    public static BragEntry findDuplicate(LocalDate date, String achievement, String repo) {
        return find("date = ?1 and achievement = ?2 and repo = ?3 and deleted = false", date, achievement, repo)
                .firstResult();
    }
}
