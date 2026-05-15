package dev.omatheusmesmo.qlawkus.messaging.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppEvent(
        String object,
        List<Entry> entry
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            String id,
            List<Change> changes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Change(
            Value value,
            String field
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Value(
            @JsonProperty("messaging_product") String messagingProduct,
            List<Contact> contacts,
            List<Message> messages
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contact(
            @JsonProperty("wa_id") String waId,
            Profile profile
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String id,
            String from,
            String type,
            Text text,
            Audio audio
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Text(String body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Audio(@JsonProperty("id") String fileId) {}
}
