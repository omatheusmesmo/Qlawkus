package dev.omatheusmesmo.qlawkus.dto;

/** One exact replacement in a file-edit batch, applied in the order supplied. */
public record FileEdit(String oldText, String newText) {
}
