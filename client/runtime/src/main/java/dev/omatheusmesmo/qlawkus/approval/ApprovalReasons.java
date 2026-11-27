package dev.omatheusmesmo.qlawkus.approval;

import java.util.Optional;

/**
 * Says why a tool waits for the owner for a reason other than the permission matrix: the owner listed
 * it in {@code qlawkus.approval.tools}, or an extension requires it. Used to explain a tool's level.
 */
@FunctionalInterface
public interface ApprovalReasons {

    /** A phrase such as "listed in qlawkus.approval.tools", or empty when nothing but the matrix applies. */
    Optional<String> reasonFor(String toolName);
}
