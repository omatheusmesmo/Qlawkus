package dev.omatheusmesmo.qlawkus.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record FileEntry(
        String name,
        String type,
        long size,
        long lastModifiedMs
) {
}
