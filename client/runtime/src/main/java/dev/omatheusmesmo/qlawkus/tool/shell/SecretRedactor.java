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
 * transcripts or logs: every value passed through {@code qlawkus.shell.secret-env} and any string
 * shaped like a GitHub token. Redaction stops accidental leaks, not a command that encodes the value
 * on purpose.
 */
@ApplicationScoped
public class SecretRedactor {

    static final String MASK = "[redacted]";

    static final int MIN_SECRET_LENGTH = 8;

    private static final Pattern GITHUB_TOKEN =
            Pattern.compile("\\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{22,})\\b");

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
        return GITHUB_TOKEN.matcher(result).replaceAll(MASK);
    }
}
