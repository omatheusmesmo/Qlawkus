package dev.omatheusmesmo.qlawkus.tools.google.gmail.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GmailModifyRequest(
    List<String> addLabelIds,
    List<String> removeLabelIds) {
}
