package dev.omatheusmesmo.qlawkus.artifact;

import io.smallrye.mutiny.Uni;

/** Optional transport for sending a registered artifact to its bound conversation. */
public interface ArtifactDelivery {

    Uni<Void> send(String providerId, String chatId, ArtifactLibrary.Content content);
}
