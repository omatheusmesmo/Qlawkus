package dev.omatheusmesmo.qlawkus.tools.google.storage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StorageBucketList(
        List<StorageBucket> items
) {
}
