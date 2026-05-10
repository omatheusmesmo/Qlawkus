package dev.omatheusmesmo.qlawkus.tools.google.gmail;

import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleAuthHeadersFilter;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailMessage;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailMessageList;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailSendRequest;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

@Path("/gmail/v1/users")
@RegisterRestClient(baseUri = "https://www.googleapis.com")
@RegisterProvider(GoogleAuthHeadersFilter.class)
public interface GoogleGmailRestClient {

    @GET
    @Path("/{userId}/messages")
    GmailMessageList listMessages(
            @PathParam("userId") String userId,
            @QueryParam("maxResults") Integer maxResults,
            @QueryParam("q") String query);

    @GET
    @Path("/{userId}/messages/{messageId}")
    GmailMessage getMessage(
            @PathParam("userId") String userId,
            @PathParam("messageId") String messageId,
            @QueryParam("format") String format);

    @POST
    @Path("/{userId}/messages/send")
    GmailMessage sendMessage(
            @PathParam("userId") String userId,
            GmailSendRequest request);
}
