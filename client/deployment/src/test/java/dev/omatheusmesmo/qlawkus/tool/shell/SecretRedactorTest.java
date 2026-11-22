package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.secrets.SecretPropertyCatalog;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretRedactorTest {

    @Test
    void redact_replacesEveryOccurrenceOfAConfiguredValue() {
        String out = SecretRedactor.redact("a=hunter2hunter2 b=hunter2hunter2", List.of("hunter2hunter2"));

        assertEquals("a=[redacted] b=[redacted]", out);
    }

    @Test
    void redact_ignoresValuesShorterThanEightCharacters() {
        String out = SecretRedactor.redact("the value is abc and 1234567", List.of("abc", "1234567"));

        assertEquals("the value is abc and 1234567", out);
    }

    @Test
    void redact_ignoresBlankAndNullValues() {
        String out = SecretRedactor.redact("nothing changes", Arrays.asList("", "        ", null));

        assertEquals("nothing changes", out);
    }

    @Test
    void redact_treatsConfiguredValuesAsLiteralsNotRegex() {
        String out = SecretRedactor.redact("tok.*en+(x) and tokXXen", List.of("tok.*en+(x)"));

        assertEquals("[redacted] and tokXXen", out);
    }

    @Test
    void redact_longerSecretWinsOverAShorterOneItContains() {
        String out = SecretRedactor.redact("v=abcdefgh-extra", List.of("abcdefgh", "abcdefgh-extra"));

        assertEquals("v=[redacted]", out);
    }

    @Test
    void redact_catchesGithubTokenShapesEvenWhenNotConfigured() {
        String classic = "ghp_" + "A1b2C3d4".repeat(5);
        String fine = "github_pat_" + "Ab1_".repeat(8);
        String oauth = "gho_" + "x".repeat(36);
        String server = "ghs_" + "y".repeat(40);

        String out = SecretRedactor.redact(
                "c=" + classic + " f=" + fine + " o=" + oauth + " s=" + server, List.of());

        assertEquals("c=[redacted] f=[redacted] o=[redacted] s=[redacted]", out);
    }

    @Test
    void redact_leavesLookalikesThatAreTooShortAlone() {
        String text = "ghp_short and github_pat_tiny and ghx_" + "z".repeat(40);

        assertEquals(text, SecretRedactor.redact(text, List.of()));
    }

    @Test
    void redact_nullInputStaysNull() {
        assertNull(SecretRedactor.redact(null, List.of("abcdefgh")));
    }

    @Test
    void redact_masksATelegramBotTokenInsideAUrlPath() {
        String token = "123456789:" + "AAFx_y-Z".repeat(4) + "abc";

        String out = SecretRedactor.redact(
                "POST https://api.telegram.org/bot" + token + "/sendMessage timed out", List.of());

        assertEquals("POST https://api.telegram.org/bot[redacted]/sendMessage timed out", out);
    }

    @Test
    void redact_masksCredentialHeadersAndBearerTokens() {
        String out = SecretRedactor.redact(
                "headers: Authorization: Bearer abcDEF123456789 x-api-key=zzzzzzzzzzzz done", List.of());

        assertEquals("headers: Authorization: [redacted] x-api-key=[redacted] done", out);
    }

    @Test
    void redact_masksTelegramWebhookCredentials() {
        assertTrue(SecretPropertyCatalog.isSecret("qlawkus.messaging.telegram.webhook-secret-token"));
        String out = SecretRedactor.redact(
                "X-Telegram-Bot-Api-Secret-Token: test-secret_42 {\"secret_token\":\"test-secret_42\"}", List.of());

        assertEquals("X-Telegram-Bot-Api-Secret-Token: [redacted] {\"secret_token\":\"[redacted]\"}", out);
    }

    @Test
    void redact_masksKnownProviderKeyShapes() {
        String nvidia = "nvapi-" + "Ab1_-".repeat(10);
        String openai = "sk-" + "Ab1".repeat(10);
        String groq = "gsk_" + "Ab1".repeat(10);
        String slack = "xoxb-" + "1234567890-".repeat(2) + "AbCdEfGh";

        String out = SecretRedactor.redact(
                "n=" + nvidia + " o=" + openai + " g=" + groq + " s=" + slack, List.of());

        assertEquals("n=[redacted] o=[redacted] g=[redacted] s=[redacted]", out);
    }

    @Test
    void redact_masksCredentialQueryParametersButKeepsTheRestOfTheUrl() {
        String out = SecretRedactor.redact(
                "GET https://x.test/v1/search?q=hello&api_key=secretvalue123&page=2 failed", List.of());

        assertEquals("GET https://x.test/v1/search?q=hello&api_key=[redacted]&page=2 failed", out);
    }

    @Test
    void redact_masksADiscordBotToken() {
        String token = "MTA" + "x".repeat(21) + "." + "AbCdEf" + "." + "y".repeat(27);

        String out = SecretRedactor.redact("login failed for " + token, List.of());

        assertEquals("login failed for [redacted]", out);
    }

    @Test
    void redact_leavesOrdinaryProseAndShortWordsAlone() {
        String text = "HTTP 401 Unauthorized: token expired for user task-sk-1 and the key was rotated";

        assertEquals(text, SecretRedactor.redact(text, List.of()));
    }
}
