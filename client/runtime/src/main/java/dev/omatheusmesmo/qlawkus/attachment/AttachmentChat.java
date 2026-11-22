package dev.omatheusmesmo.qlawkus.attachment;

import java.util.List;

/** Optional analysis of owner-supplied documents and images for the current turn. */
public interface AttachmentChat {
    String answer(String question, List<ChatAttachment> attachments);
}
