package dev.omatheusmesmo.qlawkus.dto;

import java.util.List;

public record MemorySummary(
    List<String> embeddingSources,
    long journalCount,
    long chatMessageCount
) {}
