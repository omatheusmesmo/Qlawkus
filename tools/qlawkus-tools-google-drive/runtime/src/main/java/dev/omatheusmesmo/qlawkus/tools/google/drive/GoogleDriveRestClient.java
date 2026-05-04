package dev.omatheusmesmo.qlawkus.tools.google.drive;

import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleAuthHeadersFilter;
import dev.omatheusmesmo.qlawkus.tools.google.drive.model.DriveFile;
import dev.omatheusmesmo.qlawkus.tools.google.drive.model.DriveFileList;
import dev.omatheusmesmo.qlawkus.tools.google.drive.model.DrivePermission;
import dev.omatheusmesmo.qlawkus.tools.google.drive.model.DrivePermissionRequest;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

@Path("/drive/v3/files")
@RegisterRestClient(baseUri = "https://www.googleapis.com")
@RegisterProvider(GoogleAuthHeadersFilter.class)
public interface GoogleDriveRestClient {

    @GET
    DriveFileList listFiles(
            @QueryParam("pageSize") Integer pageSize,
            @QueryParam("q") String query,
            @QueryParam("fields") String fields);

    @GET
    @Path("/{fileId}")
    DriveFile getFile(
            @PathParam("fileId") String fileId,
            @QueryParam("fields") String fields);

    @POST
    @Path("/{fileId}/permissions")
    DrivePermission createPermission(
            @PathParam("fileId") String fileId,
            DrivePermissionRequest request,
            @QueryParam("fields") String fields);
}
