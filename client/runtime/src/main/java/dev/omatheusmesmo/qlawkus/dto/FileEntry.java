package dev.omatheusmesmo.qlawkus.dto;

public record FileEntry(
        String name,
        String type,
        long size,
        long lastModifiedMs
) {
}
