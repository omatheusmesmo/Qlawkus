package dev.omatheusmesmo.qlawkus.tools.google.storage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StorageBucket(
        String name,
        String location,
        String storageClass,
        String created
) {
}
