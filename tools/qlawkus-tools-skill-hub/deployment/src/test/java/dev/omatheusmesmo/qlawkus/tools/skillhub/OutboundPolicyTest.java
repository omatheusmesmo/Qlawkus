package dev.omatheusmesmo.qlawkus.tools.skillhub;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The outbound guard every skill-hub request passes through: https only, no credentials, and no
 * loopback, link-local, private, carrier-grade NAT or multicast target. Literal IP addresses are used
 * throughout, so nothing here depends on DNS.
 */
class OutboundPolicyTest {

  @ParameterizedTest
  @ValueSource(strings = {
      "127.0.0.1", "::1", "10.1.2.3", "172.16.0.9", "192.168.0.1", "169.254.169.254", "fe80::1",
      "fd00::1", "100.64.1.1", "0.0.0.0", "224.0.0.1"})
  void loopbackPrivateAndReservedAddressesAreRefused(String literal) throws UnknownHostException {
    assertTrue(OutboundPolicy.isPrivate(InetAddress.getByName(literal)), literal);
  }

  @ParameterizedTest
  @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "2606:4700:4700::1111"})
  void publicAddressesAreAllowed(String literal) throws UnknownHostException {
    assertFalse(OutboundPolicy.isPrivate(InetAddress.getByName(literal)), literal);
  }

  @Test
  void anHttpsPublicUrlPasses() {
    assertDoesNotThrow(() -> new OutboundPolicy().check(URI.create("https://8.8.8.8/SKILL.md")));
  }

  @Test
  void aLoopbackHttpsLiteralIsRefusedWithTheReason() {
    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> new OutboundPolicy().check(URI.create("https://127.0.0.1/SKILL.md")));

    assertTrue(refused.getMessage().contains("private or loopback"), refused.getMessage());
  }

  @Test
  void localhostResolvingToLoopbackIsRefused() {
    assertThrows(IllegalArgumentException.class,
        () -> new OutboundPolicy().check(URI.create("https://localhost/SKILL.md")));
  }

  @Test
  void plainHttpIsRefusedUnderTheDefaultPolicy() {
    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> new OutboundPolicy().check(URI.create("http://8.8.8.8/SKILL.md")));

    assertTrue(refused.getMessage().contains("https"), refused.getMessage());
  }

  @Test
  void aUrlWithCredentialsIsRefusedWithoutEchoingThem() {
    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> new OutboundPolicy().check(URI.create("https://owner:hunter2@8.8.8.8/SKILL.md")));

    assertFalse(refused.getMessage().contains("hunter2"), refused.getMessage());
  }

  @Test
  void theTestingPolicyAllowsLoopbackAndHttpButStillRefusesPrivateTargets() {
    OutboundPolicy testing = new OutboundPolicy(false, true);

    assertDoesNotThrow(() -> testing.check(URI.create("http://127.0.0.1:8080/SKILL.md")));
    assertThrows(IllegalArgumentException.class,
        () -> testing.check(URI.create("http://10.0.0.5/SKILL.md")));
  }
}
