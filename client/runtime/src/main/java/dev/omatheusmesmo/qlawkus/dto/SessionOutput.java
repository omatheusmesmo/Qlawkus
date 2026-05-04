package dev.omatheusmesmo.qlawkus.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public record SessionOutput(List<String> lines, boolean hasMore, int offset, boolean promptDetected) {
}
