package dev.omatheusmesmo.qlawkus.messaging.whatsapp;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "whatsapp-api")
@Path("/v18.0")
public interface WhatsAppApiClient {

    @POST
    @Path("/{phoneNumberId}/messages")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    void sendMessage(
            @PathParam("phoneNumberId") String phoneNumberId,
            @HeaderParam("Authorization") String authorization,
            SendMessageRequest request
    );

    record SendMessageRequest(
            @JsonProperty("messaging_product") String messagingProduct,
            @JsonProperty("recipient_type") String recipientType,
            String to,
            String type,
            TextBody text
    ) {
        static SendMessageRequest textTo(String to, String body) {
            return new SendMessageRequest("whatsapp", "individual", to, "text", new TextBody(body));
        }
    }

    record TextBody(String body) {}
}
