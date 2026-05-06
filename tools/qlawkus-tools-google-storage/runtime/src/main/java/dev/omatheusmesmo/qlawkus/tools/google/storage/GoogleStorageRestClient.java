package dev.omatheusmesmo.qlawkus.tools.google.storage;

import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleAuthHeadersFilter;
import dev.omatheusmesmo.qlawkus.tools.google.storage.model.StorageBucket;
import dev.omatheusmesmo.qlawkus.tools.google.storage.model.StorageBucketList;
import dev.omatheusmesmo.qlawkus.tools.google.storage.model.StorageObject;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

@Path("/storage/v1")
@RegisterRestClient(baseUri = "https://storage.googleapis.com")
@RegisterProvider(GoogleAuthHeadersFilter.class)
public interface GoogleStorageRestClient {

    @GET
    @Path("/b")
    StorageBucketList listBuckets(
            @QueryParam("project") String project);

    @GET
    @Path("/b/{bucket}/o/{object}")
    StorageObject getObjectMetadata(
            @PathParam("bucket") String bucket,
            @PathParam("object") String objectName);

    @POST
    @Path("/b/{bucket}/o")
    StorageObject uploadObject(
            @PathParam("bucket") String bucket,
            @QueryParam("uploadType") String uploadType,
            @QueryParam("name") String name,
            String content);
}
