package dev.omatheusmesmo.qlawkus.dto;

import dev.omatheusmesmo.qlawkus.permission.PermissionService;

/**
 * The API view of the outcome of a requested permission change.
 */
public record PermissionChangeView(String status, String message, String requestId) {

    public static PermissionChangeView of(PermissionService.Outcome outcome) {
        return new PermissionChangeView(outcome.status().name(), outcome.message(), outcome.requestId());
    }
}
