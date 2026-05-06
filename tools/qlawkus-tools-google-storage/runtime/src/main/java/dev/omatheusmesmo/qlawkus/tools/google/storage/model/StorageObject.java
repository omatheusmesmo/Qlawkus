package dev.omatheusmesmo.qlawkus.tools.google.storage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StorageObject(
        String name,
        String bucket,
        String contentType,
        Long size,
        String updated
) {
}
