package dev.omatheusmesmo.qlawkus.tools.google.drive.model;

public record DrivePermissionRequest(
        String type,
        String role,
        String emailAddress
) {
}
