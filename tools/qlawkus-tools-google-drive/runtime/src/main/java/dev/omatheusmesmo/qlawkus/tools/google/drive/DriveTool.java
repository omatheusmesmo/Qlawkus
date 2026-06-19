package dev.omatheusmesmo.qlawkus.tools.google.drive;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.agent.Logged;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleApiDiagnostics;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleApiExecutor;
import dev.omatheusmesmo.qlawkus.tools.google.drive.model.DriveFile;
import dev.omatheusmesmo.qlawkus.tools.google.drive.model.DriveFileList;
import dev.omatheusmesmo.qlawkus.tools.google.drive.model.DrivePermission;
import dev.omatheusmesmo.qlawkus.tools.google.drive.model.DrivePermissionRequest;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Base64;
import java.util.stream.Collectors;

@QlawTool
@ApplicationScoped
@Logged
public class DriveTool {

    @Inject
    GoogleDriveConfig config;

    @Inject
    @RestClient
    GoogleDriveRestClient driveClient;

    @Inject
    @RestClient
    GoogleDriveUploadClient uploadClient;

    @Inject
    @RestClient
    GoogleDriveDownloadClient downloadClient;

    @Inject
    GoogleApiExecutor apiExecutor;

    @Tool("List files in Google Drive. Optionally filter by query (e.g. \"name contains 'report'\", \"mimeType='application/pdf'\").")
    public String listDriveFiles(
            @P(value = "Drive query filter, e.g. \"name contains 'report'\"", required = false) String query,
            @P(value = "Maximum number of files to return", required = false) Integer maxResults) {

        int limit = maxResults != null && maxResults > 0 ? maxResults : config.maxResults();
        String fieldsParam = "files(" + config.fields() + ")";

        try {
            DriveFileList result = apiExecutor.executeWithAuthRetry(() ->
                    driveClient.listFiles(limit, query, fieldsParam));

            if (result.files() == null || result.files().isEmpty()) {
                return "No files found.";
            }

            return result.files().stream()
                    .map(this::formatFileSummary)
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            Log.errorf(e, "Failed to list Drive files");
            return GoogleApiDiagnostics.diagnose("list Drive files", e);
        }
    }

    @Tool("Upload a text file to Google Drive. Provide file name and text content.")
    public String uploadFile(
            @P("File name, e.g. 'report.txt'") String fileName,
            @P("Text content of the file") String content,
            @P(value = "MIME type (defaults to text/plain)", required = false) String mimeType) {

        String type = mimeType != null && !mimeType.isBlank() ? mimeType : "text/plain";

        try {
            String base64Content = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(content.getBytes());

            DriveFile uploaded = apiExecutor.executeWithAuthRetry(() ->
                    uploadClient.uploadSimple("media", fileName, type, base64Content));

            return "File uploaded: " + uploaded.name() + " (ID: " + uploaded.id() + ")\nLink: " + uploaded.webViewLink();
        } catch (Exception e) {
            Log.errorf(e, "Failed to upload file to Drive");
            return GoogleApiDiagnostics.diagnose("upload file to Drive", e);
        }
    }

    @Tool("Download a text file from Google Drive by its file ID. Returns the file content.")
    public String downloadFile(
            @P("Google Drive file ID") String fileId) {

        try {
            DriveFile metadata = apiExecutor.executeWithAuthRetry(() ->
                    driveClient.getFile(fileId, config.fields()));
            String content = apiExecutor.executeWithAuthRetry(() ->
                    downloadClient.downloadFile(fileId, "media"));

            if (content == null || content.isBlank()) {
                return "File '" + metadata.name() + "' appears to be empty or binary (cannot display as text).";
            }

            return "File: " + metadata.name() + "\n---\n" + content;
        } catch (Exception e) {
            Log.errorf(e, "Failed to download Drive file %s", fileId);
            return GoogleApiDiagnostics.diagnose("download Drive file", e);
        }
    }

    @Tool("Share a Google Drive file with a user. Provide file ID, email, and role (reader, writer, owner).")
    public String shareFile(
            @P("Google Drive file ID") String fileId,
            @P("Email address of the user to share with") String email,
            @P(value = "Permission role: reader, writer, or owner", required = false) String role) {

        String permissionRole = role != null && !role.isBlank() ? role : "reader";

        try {
            DrivePermission permission = apiExecutor.executeWithAuthRetry(() ->
                    driveClient.createPermission(fileId, new DrivePermissionRequest("user", permissionRole, email), "id,emailAddress,role"));
            return "File shared with " + permission.emailAddress() + " as " + permission.role();
        } catch (Exception e) {
            Log.errorf(e, "Failed to share Drive file %s", fileId);
            return GoogleApiDiagnostics.diagnose("share Drive file", e);
        }
    }

    private String formatFileSummary(DriveFile file) {
        String size = file.size() != null ? file.size() + " bytes" : "N/A";
        return String.format("%s (%s) | Type: %s | Modified: %s | Size: %s\nLink: %s",
                file.name(), file.id(), file.mimeType(), file.modifiedTime(), size,
                file.webViewLink() != null ? file.webViewLink() : "N/A");
    }
}
