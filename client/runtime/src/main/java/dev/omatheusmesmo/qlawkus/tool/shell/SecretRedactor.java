package dev.omatheusmesmo.qlawkus.tool.shell;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Masks credentials in anything a command produced before it reaches the model, working memory,
 * transcripts or logs: every value passed through {@code qlawkus.shell.secret-env}, plus anything shaped
 * like a credential whether or not it is configured: GitHub, NVIDIA, OpenAI-style, Groq, Slack, Discord
 * and Telegram tokens, {@code Authorization}-style headers and bearer tokens, and credential query
 * parameters. Redaction stops accidental leaks, not a command that encodes the value on purpose.
 */
@ApplicationScoped
public class SecretRedactor {

    static final String MASK = "[redacted]";

    static final int MIN_SECRET_LENGTH = 8;

    private static final Pattern GITHUB_TOKEN =
            Pattern.compile("\\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{22,})\\b");

    private static final Pattern PROVIDER_KEY = Pattern.compile(
            "\\b(?:nvapi-[A-Za-z0-9_-]{16,}|sk-[A-Za-z0-9_-]{16,}|gsk_[A-Za-z0-9]{16,}|xox[abprs]-[A-Za-z0-9-]{10,})");

    private static final Pattern DISCORD_TOKEN = Pattern.compile(
            "\\b[MNO][A-Za-z0-9_-]{23,25}\\.[A-Za-z0-9_-]{6}\\.[A-Za-z0-9_-]{27,}\\b");

    private static final Pattern TELEGRAM_URL_TOKEN = Pattern.compile("(?<=/bot)\\d{6,}:[A-Za-z0-9_-]{30,}");

    private static final Pattern TELEGRAM_TOKEN = Pattern.compile("\\b\\d{8,12}:[A-Za-z0-9_-]{35}\\b");

    private static final Pattern CREDENTIAL_HEADER = Pattern.compile(
            "(?i)\\b(authorization|proxy-authorization|x-api-key|api-key|apikey|x-auth-token|x-telegram-bot-api-secret-token)\\b(\\s*[:=]\\s*)"
                    + "(?:(?:Bearer|Basic|Token)\\s+)?[^\\s,;&\"']+");

    private static final Pattern TELEGRAM_WEBHOOK_JSON =
            Pattern.compile("(\"secret_token\"\\s*:\\s*\")[A-Za-z0-9_-]+(\")");

    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{8,}");

    private static final Pattern CREDENTIAL_QUERY = Pattern.compile(
            "(?i)([?&](?:api[_-]?key|key|token|access_token|auth|secret|password|signature|sig)=)[^&\\s\"']+");

    @Inject
    ChildEnvironment childEnvironment;

    public String redact(String text) {
        return redact(text, childEnvironment.configuredSecrets().values());
    }

    static String redact(String text, Collection<String> secrets) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        List<String> ordered = secrets.stream()
                .filter(Objects::nonNull)
                .filter(value -> value.length() >= MIN_SECRET_LENGTH && !value.isBlank())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String secret : ordered) {
            result = result.replace(secret, MASK);
        }
        result = GITHUB_TOKEN.matcher(result).replaceAll(MASK);
        result = CREDENTIAL_HEADER.matcher(result).replaceAll("$1$2" + MASK);
        result = TELEGRAM_WEBHOOK_JSON.matcher(result).replaceAll("$1" + MASK + "$2");
        result = BEARER.matcher(result).replaceAll("Bearer " + MASK);
        result = CREDENTIAL_QUERY.matcher(result).replaceAll("$1" + MASK);
        result = TELEGRAM_URL_TOKEN.matcher(result).replaceAll(MASK);
        result = PROVIDER_KEY.matcher(result).replaceAll(MASK);
        result = DISCORD_TOKEN.matcher(result).replaceAll(MASK);
        return TELEGRAM_TOKEN.matcher(result).replaceAll(MASK);
    }
}
