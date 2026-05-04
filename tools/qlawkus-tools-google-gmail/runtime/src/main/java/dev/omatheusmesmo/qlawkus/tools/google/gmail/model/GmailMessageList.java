package dev.omatheusmesmo.qlawkus.tools.google.gmail.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GmailMessageList(
        List<GmailMessageRef> messages,
        String nextPageToken,
        int resultSizeEstimate) {

    public GmailMessageList {
        messages = messages != null ? messages : List.of();
    }
}
