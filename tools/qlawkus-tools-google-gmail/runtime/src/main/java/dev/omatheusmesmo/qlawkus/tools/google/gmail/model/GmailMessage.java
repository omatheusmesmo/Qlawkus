package dev.omatheusmesmo.qlawkus.tools.google.gmail.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GmailMessage(
        String id,
        String threadId,
        List<GmailMessagePartHeader> headers,
        String snippet) {

    public GmailMessage {
        headers = headers != null ? headers : List.of();
    }

    public String subject() {
        return headers.stream()
                .filter(h -> "Subject".equalsIgnoreCase(h.name()))
                .map(GmailMessagePartHeader::value)
                .findFirst()
                .orElse("(no subject)");
    }

    public String from() {
        return headers.stream()
                .filter(h -> "From".equalsIgnoreCase(h.name()))
                .map(GmailMessagePartHeader::value)
                .findFirst()
                .orElse("(unknown)");
    }

    public String date() {
        return headers.stream()
                .filter(h -> "Date".equalsIgnoreCase(h.name()))
                .map(GmailMessagePartHeader::value)
                .findFirst()
                .orElse("");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GmailMessagePartHeader(
            String name,
            String value) {
    }
}
