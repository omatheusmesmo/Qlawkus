package dev.omatheusmesmo.qlawkus.tools.google.drive;

import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleAuthHeadersFilter;
import dev.omatheusmesmo.qlawkus.tools.google.drive.model.DriveFile;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

@Path("/drive/v3/files")
@RegisterRestClient(baseUri = "https://www.googleapis.com")
@RegisterProvider(GoogleAuthHeadersFilter.class)
public interface GoogleDriveDownloadClient {

    @GET
    @Path("/{fileId}")
    String downloadFile(
            @PathParam("fileId") String fileId,
            @QueryParam("alt") String alt);
}
