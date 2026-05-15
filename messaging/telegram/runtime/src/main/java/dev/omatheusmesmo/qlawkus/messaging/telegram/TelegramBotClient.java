package dev.omatheusmesmo.qlawkus.messaging.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
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

    record SendMessageRequest(
            @JsonProperty("chat_id") String chatId,
            String text,
            @JsonProperty("parse_mode") String parseMode
    ) {}
}
