package dev.omatheusmesmo.qlawkus.tools.google.drive;

import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleAuthHeadersFilter;
import dev.omatheusmesmo.qlawkus.tools.google.drive.model.DriveFile;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.HeaderParam;

@RegisterRestClient(baseUri = "https://www.googleapis.com")
@RegisterProvider(GoogleAuthHeadersFilter.class)
public interface GoogleDriveUploadClient {

    @POST
    @Path("/upload/drive/v3/files")
    DriveFile uploadSimple(
            @QueryParam("uploadType") String uploadType,
            @QueryParam("name") String name,
            @HeaderParam("Content-Type") String contentType,
            String content);
}
