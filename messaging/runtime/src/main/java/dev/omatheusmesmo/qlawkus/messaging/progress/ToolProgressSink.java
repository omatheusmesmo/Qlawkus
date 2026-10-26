package dev.omatheusmesmo.qlawkus.messaging.progress;

/**
 * The platform operations a status message needs. Each call blocks until the platform answers and
 * throws on failure; {@link ToolProgressReporter} turns the first failure into a stopped progress
 * message rather than a failed turn.
 */
public interface ToolProgressSink {

    /** Posts a new status message and returns the platform id that later edits and deletes use. */
    String post(String chatId, String text);

    void edit(String chatId, String messageId, String text);

    void delete(String chatId, String messageId);
}
