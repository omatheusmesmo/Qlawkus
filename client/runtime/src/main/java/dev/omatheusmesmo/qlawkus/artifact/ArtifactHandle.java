package dev.omatheusmesmo.qlawkus.artifact;

import java.time.Instant;
import java.util.UUID;

/** Public metadata for an artifact. The id carries no workspace or delivery information. */
public record ArtifactHandle(UUID id, String name, String mediaType, int size, Instant createdAt, Instant expiresAt) {

    public String downloadUrl() {
        return "/api/artifacts/" + id;
    }
}
