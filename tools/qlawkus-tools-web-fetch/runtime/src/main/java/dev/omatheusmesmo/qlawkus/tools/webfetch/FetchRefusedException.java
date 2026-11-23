package dev.omatheusmesmo.qlawkus.tools.webfetch;

/** A URL the policy will not let the agent fetch. The message is written for the model and the owner. */
public class FetchRefusedException extends IllegalArgumentException {

    public FetchRefusedException(String message) {
        super(message);
    }
}
