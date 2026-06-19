package dev.omatheusmesmo.qlawkus.tools.google.gmail;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.agent.Logged;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleApiDiagnostics;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleApiExecutor;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailMessage;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailMessageList;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailModifyRequest;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailSendRequest;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@QlawTool
@ApplicationScoped
@Logged
public class GmailTool {

    @Inject
    GoogleGmailConfig config;

    @Inject
    @RestClient
    GoogleGmailRestClient gmailClient;

    @Inject
    GoogleApiExecutor apiExecutor;

    private final ConcurrentHashMap<String, Long> recentSends = new ConcurrentHashMap<>();

    private static final long SEND_DEDUP_WINDOW_MS = 30_000;

    @Tool("List recent Gmail messages. Optionally filter by query (e.g. 'is:unread', 'from:someone@example.com').")
    public String listEmails(
        @P(value = "Gmail query filter, e.g. 'is:unread'", required = false) String query,
        @P(value = "Maximum number of messages to return", required = false) Integer maxResults) {

        int limit = maxResults != null && maxResults > 0 ? maxResults : config.maxResults();

        try {
            GmailMessageList result = apiExecutor.executeWithAuthRetry(() ->
                gmailClient.listMessages(config.userId(), limit, query));

            if (result.messages().isEmpty()) {
                return "No messages found.";
            }

            return result.messages().stream()
                .map(ref -> {
                    try {
                        GmailMessage msg = apiExecutor.executeWithAuthRetry(() ->
                            gmailClient.getMessage(config.userId(), ref.id(), "metadata"));
                        return formatMessageSummary(msg);
                    } catch (Exception e) {
                        return ref.id() + ": (failed to load)";
                    }
                })
                .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            Log.errorf(e, "Failed to list Gmail messages");
            return GoogleApiDiagnostics.diagnose("list Gmail messages", e);
        }
    }

    @Tool("Get the full content of a single Gmail message by its ID. Returns subject, sender, date, and body text.")
    public String getEmail(
        @P("The Gmail message ID") String messageId) {

        try {
            GmailMessage msg = apiExecutor.executeWithAuthRetry(() ->
                gmailClient.getMessage(config.userId(), messageId, "full"));
            return formatMessageFull(msg);
        } catch (Exception e) {
            Log.errorf(e, "Failed to get Gmail message %s", messageId);
            return GoogleApiDiagnostics.diagnose("get Gmail message", e);
        }
    }

    @Tool("Send a Gmail message. Provide recipient email, subject and body text.")
    public String sendEmail(
        @P("Recipient email address") String to,
        @P("Email subject") String subject,
        @P("Email body text") String body) {

        String dedupKey = to + "|" + subject;
        if (isDuplicateSend(dedupKey)) {
            return "Email to " + to + " with subject '" + subject + "' was already sent recently. Skipping duplicate.";
        }

        String rawEmail = buildRawEmail(to, subject, body);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(rawEmail.getBytes(StandardCharsets.UTF_8));

        try {
            apiExecutor.executeWithAuthRetry(() -> {
                gmailClient.sendMessage(config.userId(), new GmailSendRequest(encoded));
                return null;
            });
            markSent(dedupKey);
            return "Email sent to " + to + ": " + subject;
        } catch (Exception e) {
            Log.errorf(e, "Failed to send Gmail message");
            return GoogleApiDiagnostics.diagnose("send Gmail message", e);
        }
    }

    @Tool("Reply to a Gmail message. Provide the original message ID and the reply body text. " +
        "Set replyAll to true to reply to all recipients (From + To + Cc), or false to reply only to the sender. " +
        "When the original message was sent BY the user (From matches user's address), replying only to sender would go back to themselves — " +
        "prefer replyAll=true in that case so the reply goes to the original recipients. " +
        "The reply will be threaded with the original conversation.")
    public String replyToEmail(
        @P("The Gmail message ID to reply to") String messageId,
        @P("Reply body text") String body,
        @P(value = "If true, reply to all recipients (From + To + Cc). If false, reply only to sender.", required = false) Boolean replyAll) {

        try {
            GmailMessage original = apiExecutor.executeWithAuthRetry(() ->
                gmailClient.getMessage(config.userId(), messageId, "metadata"));

            String from = original.from();
            String to = original.to();
            String cc = original.cc();
            String replyTo = original.replyTo();
            String subject = original.subject();
            String threadId = original.threadId();
            String messageIdHeader = original.messageIdHeader();

            if (!subject.toLowerCase().startsWith("re:")) {
                subject = "Re: " + subject;
            }

            String dedupKey = "reply|" + messageId;
            if (isDuplicateSend(dedupKey)) {
                return "Reply to message " + messageId + " was already sent recently. Skipping duplicate.";
            }

            boolean allRecipients = replyAll != null ? replyAll : false;
            String recipients = resolveReplyRecipients(from, to, cc, replyTo, allRecipients);

            String rawEmail = buildReplyEmail(recipients, subject, body, messageIdHeader, messageIdHeader);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(rawEmail.getBytes(StandardCharsets.UTF_8));

        apiExecutor.executeWithAuthRetry(() -> {
            gmailClient.sendMessage(config.userId(), new GmailSendRequest(encoded, threadId));
                return null;
            });
            markSent(dedupKey);
            return "Reply sent to " + recipients + ": " + subject;
        } catch (Exception e) {
            Log.errorf(e, "Failed to reply to Gmail message %s", messageId);
            return GoogleApiDiagnostics.diagnose("reply to Gmail message", e);
        }
    }

    @Tool("Move a Gmail message to trash. Returns success confirmation.")
    public String trashEmail(
        @P("The Gmail message ID to trash") String messageId) {

        try {
            apiExecutor.executeWithAuthRetry(() -> {
                gmailClient.trashMessage(config.userId(), messageId);
                return null;
            });
            return "Message " + messageId + " moved to trash.";
        } catch (Exception e) {
            Log.errorf(e, "Failed to trash Gmail message %s", messageId);
            return GoogleApiDiagnostics.diagnose("trash Gmail message", e);
        }
    }

    @Tool("Restore a trashed Gmail message back to inbox.")
    public String untrashEmail(
        @P("The Gmail message ID to restore") String messageId) {

        try {
            apiExecutor.executeWithAuthRetry(() -> {
                gmailClient.untrashMessage(config.userId(), messageId);
                return null;
            });
            return "Message " + messageId + " restored from trash.";
        } catch (Exception e) {
            Log.errorf(e, "Failed to untrash Gmail message %s", messageId);
            return GoogleApiDiagnostics.diagnose("untrash Gmail message", e);
        }
    }

    @Tool("Modify labels on a Gmail message. Use to mark as read/unread, archive, or apply custom labels. Common label IDs: UNREAD, INBOX, STARRED, IMPORTANT, TRASH, SPAM.")
    public String modifyEmailLabels(
        @P("The Gmail message ID") String messageId,
        @P(value = "Label IDs to add, e.g. ['UNREAD', 'STARRED']", required = false) List<String> addLabelIds,
        @P(value = "Label IDs to remove, e.g. ['UNREAD', 'INBOX']", required = false) List<String> removeLabelIds) {

        if ((addLabelIds == null || addLabelIds.isEmpty()) && (removeLabelIds == null || removeLabelIds.isEmpty())) {
            return "No labels specified to add or remove.";
        }

        GmailModifyRequest request = new GmailModifyRequest(addLabelIds, removeLabelIds);

        try {
            apiExecutor.executeWithAuthRetry(() -> {
                gmailClient.modifyMessage(config.userId(), messageId, request);
                return null;
            });
            StringBuilder result = new StringBuilder("Labels updated for message " + messageId + ".");
            if (addLabelIds != null && !addLabelIds.isEmpty()) {
                result.append(" Added: ").append(addLabelIds);
            }
            if (removeLabelIds != null && !removeLabelIds.isEmpty()) {
                result.append(" Removed: ").append(removeLabelIds);
            }
            return result.toString();
        } catch (Exception e) {
            Log.errorf(e, "Failed to modify labels for Gmail message %s", messageId);
            return GoogleApiDiagnostics.diagnose("modify Gmail message labels", e);
        }
    }

    @Tool("Search Gmail messages by query. Returns matching message summaries.")
    public String searchEmails(
        @P("Gmail search query, e.g. 'subject:meeting is:unread'") String query,
        @P(value = "Maximum number of messages to return", required = false) Integer maxResults) {

        return listEmails(query, maxResults);
    }

    private boolean isDuplicateSend(String dedupKey) {
        Long lastSent = recentSends.get(dedupKey);
        return lastSent != null && (System.currentTimeMillis() - lastSent) < SEND_DEDUP_WINDOW_MS;
    }

    private void markSent(String dedupKey) {
        recentSends.put(dedupKey, System.currentTimeMillis());
        recentSends.entrySet().removeIf(e ->
            (System.currentTimeMillis() - e.getValue()) > SEND_DEDUP_WINDOW_MS);
    }

    private String formatMessageSummary(GmailMessage msg) {
        return String.format("ID: %s | %s | From: %s | Date: %s\nOpen: https://mail.google.com/mail/u/0/#inbox/%s\n%s",
            msg.id(), msg.subject(), msg.from(), msg.date(), msg.threadId(),
            msg.snippet() != null ? msg.snippet() : "");
    }

    private String formatMessageFull(GmailMessage msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("Subject: ").append(msg.subject()).append("\n");
        sb.append("From: ").append(msg.from()).append("\n");
        sb.append("Date: ").append(msg.date()).append("\n");
        sb.append("ID: ").append(msg.id()).append("\n");
        sb.append("Thread: ").append(msg.threadId()).append("\n");
        sb.append("Open in Gmail: https://mail.google.com/mail/u/0/#inbox/").append(msg.threadId()).append("\n\n");
        sb.append(msg.body());
        return sb.toString();
    }

    private String buildRawEmail(String to, String subject, String body) {
        return "To: " + to + "\r\n" +
            "Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=\r\n" +
            "Content-Type: text/plain; charset=UTF-8\r\n" +
            "Content-Transfer-Encoding: base64\r\n\r\n" +
            Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8));
    }

    private String buildReplyEmail(String to, String subject, String body, String inReplyTo, String references) {
        String replyHeader = inReplyTo != null ? "In-Reply-To: " + inReplyTo + "\r\n" : "";
        String referencesHeader = references != null ? "References: " + references + "\r\n" : "";
        return "To: " + to + "\r\n" +
            "Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=\r\n" +
            replyHeader +
            referencesHeader +
            "Content-Type: text/plain; charset=UTF-8\r\n" +
            "Content-Transfer-Encoding: base64\r\n\r\n" +
            Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8));
    }

    private String resolveReplyRecipients(String from, String to, String cc, String replyTo, boolean replyAll) {
        String sender = replyTo != null && !replyTo.isBlank() ? replyTo : from;

        if (!replyAll) {
            return sender;
        }

        Set<String> seen = new HashSet<>();
        seen.add(sender.toLowerCase());
        String userEmail = config.userId().toLowerCase();
        seen.add(userEmail);

        List<String> result = new ArrayList<>();
        result.add(sender);

        if (to != null && !to.isBlank()) {
            for (String addr : to.split(",")) {
                String trimmed = addr.trim();
                if (!seen.contains(trimmed.toLowerCase())) {
                    seen.add(trimmed.toLowerCase());
                    result.add(trimmed);
                }
            }
        }

        if (cc != null && !cc.isBlank()) {
            for (String addr : cc.split(",")) {
                String trimmed = addr.trim();
                if (!seen.contains(trimmed.toLowerCase())) {
                    seen.add(trimmed.toLowerCase());
                    result.add(trimmed);
                }
            }
        }

        return String.join(", ", result);
    }
}
