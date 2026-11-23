package dev.omatheusmesmo.qlawkus.tools.webfetch;

/** A fetch that cannot succeed by trying again. The message is safe to hand to the model as it is. */
class FetchFailure extends RuntimeException {

    FetchFailure(String message) {
        super(message);
    }
}
