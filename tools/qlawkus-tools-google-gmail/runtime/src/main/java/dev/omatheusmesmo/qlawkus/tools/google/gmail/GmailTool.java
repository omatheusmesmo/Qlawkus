package dev.omatheusmesmo.qlawkus.tools.google.gmail;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.agent.Logged;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleApiDiagnostics;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailMessage;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailMessageList;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.model.GmailSendRequest;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Base64;
import java.util.stream.Collectors;

@ClawTool
@ApplicationScoped
@Logged
public class GmailTool {

    @Inject
    GoogleGmailConfig config;

    @Inject
    @RestClient
    GoogleGmailRestClient gmailClient;

    @Tool("List recent Gmail messages. Optionally filter by query (e.g. 'is:unread', 'from:someone@example.com').")
    public String listEmails(
            @P(value = "Gmail query filter, e.g. 'is:unread'", required = false) String query,
            @P(value = "Maximum number of messages to return", required = false) Integer maxResults) {

        int limit = maxResults != null && maxResults > 0 ? maxResults : config.maxResults();

        try {
            GmailMessageList result = gmailClient.listMessages(config.userId(), limit, query);

            if (result.messages().isEmpty()) {
                return "No messages found.";
            }

            return result.messages().stream()
                    .map(ref -> {
                        try {
                            GmailMessage msg = gmailClient.getMessage(config.userId(), ref.id(), "metadata");
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

    @Tool("Send a Gmail message. Provide recipient email, subject and body text.")
    public String sendEmail(
            @P("Recipient email address") String to,
            @P("Email subject") String subject,
            @P("Email body text") String body) {

        String rawEmail = buildRawEmail(to, subject, body);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(rawEmail.getBytes());

        try {
            gmailClient.sendMessage(config.userId(), new GmailSendRequest(encoded));
            return "Email sent to " + to + ": " + subject;
        } catch (Exception e) {
            Log.errorf(e, "Failed to send Gmail message");
            return GoogleApiDiagnostics.diagnose("send Gmail message", e);
        }
    }

    @Tool("Search Gmail messages by query. Returns matching message summaries.")
    public String searchEmails(
            @P("Gmail search query, e.g. 'subject:meeting is:unread'") String query,
            @P(value = "Maximum number of messages to return", required = false) Integer maxResults) {

        return listEmails(query, maxResults);
    }

    private String formatMessageSummary(GmailMessage msg) {
        return String.format("%s | From: %s | Date: %s\n%s",
                msg.subject(), msg.from(), msg.date(),
                msg.snippet() != null ? msg.snippet() : "");
    }

    private String buildRawEmail(String to, String subject, String body) {
        return "To: " + to + "\r\n" +
                "Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes()) + "?=\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: base64\r\n\r\n" +
                Base64.getEncoder().encodeToString(body.getBytes());
    }
}
