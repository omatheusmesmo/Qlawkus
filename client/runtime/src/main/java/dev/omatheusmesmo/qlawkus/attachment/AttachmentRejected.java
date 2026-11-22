package dev.omatheusmesmo.qlawkus.attachment;

/** Refusal safe to show to the owner. Messages contain no input or provider response. */
public final class AttachmentRejected extends RuntimeException {
    public AttachmentRejected(String message) {
        super(message);
    }
}
