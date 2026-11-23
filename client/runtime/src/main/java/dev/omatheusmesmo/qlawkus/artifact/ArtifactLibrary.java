package dev.omatheusmesmo.qlawkus.artifact;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Optional read surface for the authenticated owner's artifact library. */
public interface ArtifactLibrary {

    List<ArtifactHandle> list(String owner) throws IOException;

    Optional<Content> read(UUID id, String owner) throws IOException;

    record Content(ArtifactHandle handle, byte[] bytes) {
    }
}
