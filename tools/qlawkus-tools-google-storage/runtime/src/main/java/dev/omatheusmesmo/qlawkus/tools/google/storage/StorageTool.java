package dev.omatheusmesmo.qlawkus.tools.google.storage;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tools.google.storage.model.StorageBucket;
import dev.omatheusmesmo.qlawkus.tools.google.storage.model.StorageBucketList;
import dev.omatheusmesmo.qlawkus.tools.google.storage.model.StorageObject;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Base64;
import java.util.stream.Collectors;

@ClawTool
@ApplicationScoped
public class StorageTool {

    @Inject
    GoogleStorageConfig config;

    @Inject
    @RestClient
    GoogleStorageRestClient storageClient;

    @Inject
    @RestClient
    GoogleStorageDownloadClient downloadClient;

    @Tool("List Google Cloud Storage buckets for a project. Requires project ID.")
    public String listBuckets(
            @P(value = "Google Cloud project ID", required = false) String projectId) {

        String project = projectId != null && !projectId.isBlank() ? projectId : config.projectId().orElse(null);

        if (project == null || project.isBlank()) {
            return "Error: projectId is required. Set qlawkus.google.storage.projectId or pass it as parameter.";
        }

        try {
            StorageBucketList result = storageClient.listBuckets(project);

            if (result.items() == null || result.items().isEmpty()) {
                return "No buckets found for project " + project + ".";
            }

            return result.items().stream()
                    .map(this::formatBucketSummary)
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            Log.errorf(e, "Failed to list GCS buckets for project %s", project);
            return "Error listing buckets: " + e.getMessage();
        }
    }

    @Tool("Upload a text object to a Google Cloud Storage bucket. Provide bucket name, object name, and text content.")
    public String uploadObject(
            @P("GCS bucket name") String bucketName,
            @P("Object name (key), e.g. 'reports/daily.txt'") String objectName,
            @P("Text content to upload") String content) {

        try {
            String base64Content = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(content.getBytes());

            StorageObject uploaded = storageClient.uploadObject(
                    bucketName, "media", objectName, base64Content);

            return "Object uploaded: " + uploaded.name() + " to bucket " + uploaded.bucket()
                    + " (" + uploaded.size() + " bytes)";
        } catch (Exception e) {
            Log.errorf(e, "Failed to upload object %s to bucket %s", objectName, bucketName);
            return "Error uploading object: " + e.getMessage();
        }
    }

    @Tool("Download a text object from a Google Cloud Storage bucket. Returns the object content.")
    public String downloadObject(
            @P("GCS bucket name") String bucketName,
            @P("Object name (key) to download") String objectName) {

        try {
            StorageObject metadata = storageClient.getObjectMetadata(bucketName, objectName);
            String content = downloadClient.downloadObject(bucketName, objectName, "media");

            if (content == null || content.isBlank()) {
                return "Object '" + objectName + "' appears to be empty or binary (cannot display as text).";
            }

            return "Object: " + metadata.name() + " | Bucket: " + metadata.bucket()
                    + " | Size: " + metadata.size() + " bytes\n---\n" + content;
        } catch (Exception e) {
            Log.errorf(e, "Failed to download object %s from bucket %s", objectName, bucketName);
            return "Error downloading object: " + e.getMessage();
        }
    }

    private String formatBucketSummary(StorageBucket bucket) {
        return String.format("%s | Location: %s | Class: %s | Created: %s",
                bucket.name(), bucket.location(), bucket.storageClass(), bucket.created());
    }
}
