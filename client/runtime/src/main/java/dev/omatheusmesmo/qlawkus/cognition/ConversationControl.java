package dev.omatheusmesmo.qlawkus.cognition;

import jakarta.enterprise.context.RequestScoped;

/**
 * Per-request signal that the user asked to clear the conversation. The
 * orchestrator wipes the conversation's chat memory after the turn completes.
 */
@RequestScoped
public class ConversationControl {

    private boolean clearRequested;

    public void requestClear() {
        this.clearRequested = true;
    }

    public boolean isClearRequested() {
        return clearRequested;
    }
}
