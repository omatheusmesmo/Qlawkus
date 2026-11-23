package dev.omatheusmesmo.qlawkus.approval;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Persistence for approval requests. The one operation that needs care is {@link #transition}: it
 * applies a change only while the stored record still has the expected status, which is what stops a
 * duplicate callback from executing an action a second time.
 */
public interface ApprovalStore {

    /** Persists a new request. An id that already exists is an error, never an overwrite. */
    void create(ApprovalRequest request);

    /** The request with this id, or empty. An id that is not in the store's shape is never looked up. */
    Optional<ApprovalRequest> find(String id);

    /** Every stored request, newest first. */
    List<ApprovalRequest> list();

    /**
     * Replaces the request with {@code change} applied, but only when it currently has status
     * {@code from}. Empty when the status differs, so the caller knows someone else decided first.
     */
    Optional<ApprovalRequest> transition(String id, ApprovalStatus from, UnaryOperator<ApprovalRequest> change);

    /** Proves a write can be made without leaving anything behind. Throws when it cannot. */
    void verifyWritable();
}
