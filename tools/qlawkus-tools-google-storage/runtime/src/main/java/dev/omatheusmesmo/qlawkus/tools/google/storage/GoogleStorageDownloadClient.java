package dev.omatheusmesmo.qlawkus.tools.google.storage;

import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleAuthHeadersFilter;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

@Path("/storage/v1")
@RegisterRestClient(baseUri = "https://storage.googleapis.com")
@RegisterProvider(GoogleAuthHeadersFilter.class)
public interface GoogleStorageDownloadClient {

    @GET
    @Path("/b/{bucket}/o/{object}")
    String downloadObject(
            @PathParam("bucket") String bucket,
            @PathParam("object") String objectName,
            @QueryParam("alt") String alt);
}
