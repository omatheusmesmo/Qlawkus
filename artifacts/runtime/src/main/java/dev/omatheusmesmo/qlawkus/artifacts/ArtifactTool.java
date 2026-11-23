package dev.omatheusmesmo.qlawkus.artifacts;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.artifact.ArtifactDelivery;
import dev.omatheusmesmo.qlawkus.artifacts.LocalArtifactStore.Destination;
import dev.omatheusmesmo.qlawkus.config.AdminAuthConfig;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@QlawTool
@ApplicationScoped
public class ArtifactTool {

    private final WorkspaceArtifact workspace;
    private final LocalArtifactStore store;
    private final AdminAuthConfig admin;
    private final AgentDeliveryContext context;
    private final Instance<ArtifactDelivery> delivery;

    ArtifactTool(WorkspaceArtifact workspace, LocalArtifactStore store, AdminAuthConfig admin,
            AgentDeliveryContext context, Instance<ArtifactDelivery> delivery) {
        this.workspace = workspace;
        this.store = store;
        this.admin = admin;
        this.context = context;
        this.delivery = delivery;
    }

    @Tool("Register a generated UTF-8 Markdown, text or CSV file when the owner asks for a downloadable file. "
            + "Create the file in the workspace first. Returns the artifact ID, file name, media type, byte size, "
            + "expiration and authenticated download URL. Registration alone does not send the file to chat. "
            + "Only register results intended for the owner, excluding credentials, private keys and configuration.")
    public String registerArtifact(@P("Path of the generated .md, .txt or .csv file inside the workspace.") String path) {
        var origin = context.snapshot();
        Destination destination = destination(origin);
        if (origin.memoryId() == null || (destination == null && !admin.username().equals(origin.authenticatedOwner()))) {
            return "An authenticated owner conversation is required to register an artifact.";
        }
        try {
            var handle = store.save(admin.username(), destination, workspace.read(path));
            return "Artifact %s saved. Name: %s. Type: %s. Size: %d bytes. Expires: %s. Download: %s"
                    .formatted(handle.id(), handle.name(), handle.mediaType(), handle.size(), handle.expiresAt(),
                            handle.downloadUrl());
        } catch (IllegalArgumentException rejected) {
            return rejected.getMessage();
        } catch (IOException unavailable) {
            return "The workspace file could not be read or the artifact could not be saved.";
        }
    }

    @Tool("Send a registered artifact as a file only when the owner asks to receive it in the current messaging "
            + "conversation. Use the ID returned by registerArtifact. The file must have been registered in this "
            + "same conversation. In the web console, give the authenticated download link instead.")
    public String sendArtifact(@P("Opaque artifact ID returned by registerArtifact.") String artifactId) {
        Destination destination = destination(context.snapshot());
        if (destination == null || !delivery.isResolvable()) {
            return "File delivery requires the original messaging conversation and an available file provider.";
        }
        UUID id = LocalArtifactStore.parseId(artifactId);
        if (id == null) {
            return "Give the artifact ID returned by registerArtifact.";
        }
        try {
            var content = store.read(id, admin.username(), destination);
            if (content.isEmpty()) {
                return "The artifact is unavailable in this conversation.";
            }
            delivery.get().send(destination.providerId(), destination.chatId(), content.get())
                    .await().atMost(Duration.ofMinutes(2));
            return "File delivered: " + content.get().handle().name() + ".";
        } catch (IOException | RuntimeException failure) {
            Log.warnf("Artifact delivery failed provider=%s error=%s", destination.providerId(),
                    failure.getClass().getSimpleName());
            return "The file could not be delivered. The owner can still download it from the console while it is available.";
        }
    }

    private static Destination destination(AgentDeliveryContext.Snapshot origin) {
        return origin.memoryId() != null && origin.providerId() != null && origin.chatId() != null
                ? new Destination(origin.providerId(), origin.chatId()) : null;
    }
}
