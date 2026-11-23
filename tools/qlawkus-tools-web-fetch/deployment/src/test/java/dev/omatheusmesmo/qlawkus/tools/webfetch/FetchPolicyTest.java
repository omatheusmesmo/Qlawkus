package dev.omatheusmesmo.qlawkus.tools.webfetch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FetchPolicyTest {

    private final FetchPolicy policy = new FetchPolicy(List.of("blocked.example", "10.9.9.9"), false);

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/", "http://127.1.2.3:8080/x", "http://localhost/", "http://10.0.0.5/",
            "http://192.168.1.1/", "http://172.16.0.1/", "http://172.31.255.255/", "http://169.254.169.254/latest/meta-data/",
            "http://100.64.0.1/", "http://0.0.0.0/", "http://[::1]/", "http://[fc00::1]/", "http://[fe80::1]/",
            "http://[::ffff:127.0.0.1]/", "http://[::ffff:10.0.0.1]/", "http://2130706433/",
            "http://224.0.0.1/", "http://[64:ff9b::7f00:1]/", "http://[2002:7f00:1::]/", "http://192.0.0.8/", "http://198.18.0.1/", "http://240.0.0.1/"})
    void refusesEveryNonPublicAddress(String url) {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, () -> policy.check(URI.create(url)));
        assertTrue(refusal.getMessage().contains("private") || refusal.getMessage().contains("loopback"), refusal.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://93.184.216.34/file", "file:///etc/passwd", "gopher://93.184.216.34/", "jar:http://93.184.216.34/a!/"})
    void refusesSchemesOtherThanHttpAndHttps(String url) {
        assertThrows(IllegalArgumentException.class, () -> policy.check(URI.create(url)));
    }

    @Test
    void refusesCredentialsInTheUrlWithoutEchoingThem() {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> policy.check(URI.create("https://admin:hunter2@93.184.216.34/")));
        assertTrue(!refusal.getMessage().contains("hunter2"), refusal.getMessage());
    }

    @Test
    void refusesAUrlWithoutAHost() {
        assertThrows(IllegalArgumentException.class, () -> policy.check(URI.create("https:///path")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://blocked.example/", "https://BLOCKED.example/", "https://www.blocked.example/a", "https://deep.sub.blocked.example/"})
    void refusesBlockedDomainsAndTheirSubdomains(String url) {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, () -> policy.check(URI.create(url)));
        assertTrue(refusal.getMessage().contains("blocked"), refusal.getMessage());
    }

    @Test
    void aLookalikeDomainIsNotBlocked() {
        try {
            policy.check(URI.create("https://notblocked.example.net/"));
        } catch (FetchRefusedException refusal) {
            throw new AssertionError("a lookalike must not be refused: " + refusal.getMessage(), refusal);
        } catch (IllegalStateException unresolvable) {
            assertTrue(unresolvable.getMessage().contains("resolve"));
        }
    }

    @Test
    void anAddressEntryBlocksThatAddressOnlyNeverAnotherEndingTheSame() {
        FetchPolicy partial = new FetchPolicy(List.of("4.4"), false);
        assertDoesNotThrow(() -> partial.check(URI.create("https://8.8.4.4/")));
    }

    @Test
    void refusesABlockedAddressLiteral() {
        assertThrows(IllegalArgumentException.class, () -> policy.check(URI.create("https://10.9.9.9/")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://93.184.216.34/", "http://93.184.216.34:8080/a?b=c", "https://[2606:2800:220:1:248:1893:25c8:1946]/"})
    void allowsPublicHttpAndHttpsAddresses(String url) {
        assertDoesNotThrow(() -> policy.check(URI.create(url)));
    }

    @Test
    void anUnresolvableHostIsNotAPolicyRefusal() {
        assertThrows(IllegalStateException.class, () -> policy.check(URI.create("https://no-such-host.invalid/")));
    }
}
