package dev.omatheusmesmo.qlawkus.tools.google.gmail.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GmailMessage(
    String id,
    String threadId,
    Payload payload,
    String snippet) {

    public GmailMessage {
        payload = payload != null ? payload : new Payload(List.of(), null, null);
    }

    public String subject() {
        return payload.headers().stream()
            .filter(h -> "Subject".equalsIgnoreCase(h.name()))
            .map(GmailMessagePartHeader::value)
            .findFirst()
            .orElse("(no subject)");
    }

    public String from() {
        return payload.headers().stream()
            .filter(h -> "From".equalsIgnoreCase(h.name()))
            .map(GmailMessagePartHeader::value)
            .findFirst()
            .orElse("(unknown)");
    }

    public String to() {
        return payload.headers().stream()
            .filter(h -> "To".equalsIgnoreCase(h.name()))
            .map(GmailMessagePartHeader::value)
            .findFirst()
            .orElse("");
    }

    public String cc() {
        return payload.headers().stream()
            .filter(h -> "Cc".equalsIgnoreCase(h.name()))
            .map(GmailMessagePartHeader::value)
            .findFirst()
            .orElse("");
    }

    public String replyTo() {
        return payload.headers().stream()
            .filter(h -> "Reply-To".equalsIgnoreCase(h.name()))
            .map(GmailMessagePartHeader::value)
            .findFirst()
            .orElse("");
    }

    public String date() {
        return payload.headers().stream()
            .filter(h -> "Date".equalsIgnoreCase(h.name()))
            .map(GmailMessagePartHeader::value)
            .findFirst()
            .orElse("");
    }

    public String messageIdHeader() {
        return payload.headers().stream()
            .filter(h -> "Message-ID".equalsIgnoreCase(h.name()))
            .map(GmailMessagePartHeader::value)
            .findFirst()
            .orElse(null);
    }

    public String body() {
        if (payload.parts() != null) {
            for (GmailMessagePart part : payload.parts()) {
                String text = extractTextFromPart(part);
                if (text != null) return text;
            }
        }
        if (payload.body() != null && payload.body().data() != null) {
            return decodeBase64Url(payload.body().data());
        }
        return "";
    }

    private String extractTextFromPart(GmailMessagePart part) {
        if ("text/plain".equals(part.mimeType()) && part.body() != null && part.body().data() != null) {
            return decodeBase64Url(part.body().data());
        }
        if (part.parts() != null) {
            for (GmailMessagePart sub : part.parts()) {
                String text = extractTextFromPart(sub);
                if (text != null) return text;
            }
        }
        return null;
    }

    private static String decodeBase64Url(String data) {
        byte[] bytes = java.util.Base64.getUrlDecoder().decode(data);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
        List<GmailMessagePartHeader> headers,
        List<GmailMessagePart> parts,
        GmailMessageBody body) {

        public Payload {
            headers = headers != null ? headers : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GmailMessagePart(
        String mimeType,
        List<GmailMessagePart> parts,
        GmailMessageBody body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GmailMessageBody(
        String data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GmailMessagePartHeader(
        String name,
        String value) {
    }
}
