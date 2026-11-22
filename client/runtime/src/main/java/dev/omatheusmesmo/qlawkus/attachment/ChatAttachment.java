package dev.omatheusmesmo.qlawkus.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** One request-owned attachment. A channel resolves its own platform's handle after authorization. */
public record ChatAttachment(String mediaType, Long size, Supplier<byte[]> content) {

    public static final int MAX_COUNT = 3;
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_QUESTION_CHARS = 16_000;

    public ChatAttachment {
        Objects.requireNonNull(content);
    }

    public static void checkCount(List<ChatAttachment> attachments) {
        if (attachments.isEmpty() || attachments.size() > MAX_COUNT) {
            throw new AttachmentRejected("Send between one and three attachments per message.");
        }
    }

    public byte[] read() {
        if (size != null) checkSize(size);
        try {
            byte[] bytes = content.get();
            checkSize(bytes.length);
            return bytes;
        } catch (AttachmentRejected rejected) {
            throw rejected;
        } catch (RuntimeException unavailable) {
            // A supplier may throw an exception containing a URL or credential. Keep only a safe refusal.
            throw new AttachmentRejected("Could not read the attachment from its source.");
        }
    }

    public static byte[] readBounded(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        checkSize(bytes.length);
        return bytes;
    }

    public static void checkSize(long size) {
        if (size <= 0 || size > MAX_BYTES) {
            throw new AttachmentRejected("Each attachment must contain between 1 byte and 2 MiB.");
        }
    }
}
