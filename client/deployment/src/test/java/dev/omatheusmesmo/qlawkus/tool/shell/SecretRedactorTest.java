package dev.omatheusmesmo.qlawkus.tool.shell;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
