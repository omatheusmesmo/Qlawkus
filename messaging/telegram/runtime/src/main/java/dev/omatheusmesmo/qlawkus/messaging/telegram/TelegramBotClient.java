package dev.omatheusmesmo.qlawkus.messaging.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "telegram-bot-api")
@Path("/bot{token}")
public interface TelegramBotClient {

    @POST
    @Path("/sendMessage")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    void sendMessage(@PathParam("token") String token, SendMessageRequest request);

    @GET
    @Path("/getFile")
    @Produces(MediaType.APPLICATION_JSON)
    GetFileResponse getFile(@PathParam("token") String token, @QueryParam("file_id") String fileId);

    @GET
    @Path("/getUpdates")
    @Produces(MediaType.APPLICATION_JSON)
    GetUpdatesResponse getUpdates(@PathParam("token") String token,
                                  @QueryParam("offset") long offset,
                                  @QueryParam("timeout") int timeout);

    @GET
    @Path("/deleteWebhook")
    @Produces(MediaType.APPLICATION_JSON)
    void deleteWebhook(@PathParam("token") String token);

    record SendMessageRequest(
            @JsonProperty("chat_id") String chatId,
            String text,
            @JsonProperty("parse_mode") String parseMode
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GetFileResponse(boolean ok, FileResult result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FileResult(@JsonProperty("file_path") String filePath) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GetUpdatesResponse(boolean ok, List<TelegramUpdate> result) {}
}
