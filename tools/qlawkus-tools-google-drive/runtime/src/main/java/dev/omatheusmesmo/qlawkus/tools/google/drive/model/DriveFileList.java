package dev.omatheusmesmo.qlawkus.tools.google.drive.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DriveFileList(
        String nextPageToken,
        List<DriveFile> files
) {
}
