package dev.omatheusmesmo.qlawkus.tools.google.drive.model;

/** The body of a Drive file update that moves a file into or out of the trash. */
public record DriveTrashRequest(boolean trashed) {
}
