package dev.omatheusmesmo.qlawkus.tools.google.drive.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DrivePermission(
        String id,
        String type,
        String role,
        String emailAddress
) {
}
