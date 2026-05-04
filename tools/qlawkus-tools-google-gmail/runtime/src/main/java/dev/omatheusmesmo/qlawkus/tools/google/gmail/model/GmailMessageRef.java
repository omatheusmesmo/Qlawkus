package dev.omatheusmesmo.qlawkus.tools.google.gmail.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GmailMessageRef(
        String id,
        String threadId) {
}
