package dev.omatheusmesmo.qlawkus.dto;

import java.util.List;

public record SessionOutput(List<String> lines, boolean hasMore, int offset, boolean promptDetected) {
}
