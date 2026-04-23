package dev.omatheusmesmo.qlawkus.dto;

import java.time.Instant;
import java.time.LocalDate;

public record JournalSummary(
    Long id,
    LocalDate date,
    String summary,
    int messageCount,
    Instant createdAt
) {}
