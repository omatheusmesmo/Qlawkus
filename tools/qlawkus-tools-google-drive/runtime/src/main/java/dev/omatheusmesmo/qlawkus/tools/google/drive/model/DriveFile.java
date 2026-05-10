package dev.omatheusmesmo.qlawkus.tools.google.drive.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DriveFile(
        String id,
        String name,
        String mimeType,
        String modifiedTime,
        Long size,
        String webViewLink
) {
}
