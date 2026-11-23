package dev.omatheusmesmo.qlawkus.approval;

/**
 * Tells the owner about a request and hands a decided request back to the agent. The approval service
 * depends on this rather than on CDI events so its state machine can run without a container.
 */
public interface ApprovalNotifier {

    /** Sends {@code text} to the chat the request came from. Does nothing when there is no such chat. */
    void notifyOrigin(ApprovalRequest request, String text);

    /** Reports an owner decision whose outcome the agent should be told about so it can continue. */
    void decided(ApprovalRequest request);
}
