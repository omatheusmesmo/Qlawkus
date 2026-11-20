package dev.omatheusmesmo.qlawkus.tools.skillhub;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Decides whether a URL may be fetched on the owner's behalf. Every request the skill hub makes, the
 * first one and each redirect hop, is checked here before a connection is opened: the scheme must be
 * https, the URL must carry no credentials, and every address the host resolves to must be public.
 *
 * <p>A {@link DefaultBean} so the production rules hold unless a test profile supplies an alternative
 * that is explicitly lenient for a local WireMock server; the protected constructor exists for that.
 */
@ApplicationScoped
@DefaultBean
public class OutboundPolicy {

  private static final int CARRIER_GRADE_NAT_SECOND_OCTET_MASK = 0xc0;
  private static final int CARRIER_GRADE_NAT_SECOND_OCTET = 64;
  private static final int UNIQUE_LOCAL_MASK = 0xfe;
  private static final int UNIQUE_LOCAL_PREFIX = 0xfc;

  private final boolean httpsOnly;
  private final boolean allowLoopback;

  public OutboundPolicy() {
    this(true, false);
  }

  protected OutboundPolicy(boolean httpsOnly, boolean allowLoopback) {
    this.httpsOnly = httpsOnly;
    this.allowLoopback = allowLoopback;
  }

  /**
   * Refuses {@code uri} unless it may be fetched.
   *
   * @throws IllegalArgumentException with the reason when the URL is refused
   * @throws IllegalStateException when the host cannot be resolved at all
   */
  public void check(URI uri) {
    if (httpsOnly && !"https".equalsIgnoreCase(uri.getScheme())) {
      throw new IllegalArgumentException("Refused '" + uri.getScheme() + "://" + uri.getHost()
          + "': only https:// links are fetched.");
    }
    if (uri.getUserInfo() != null) {
      throw new IllegalArgumentException("Refused a URL with credentials in it (host " + uri.getHost()
          + "). Paste the plain link without user:password.");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("Refused '" + uri + "': the link has no host name.");
    }
    InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(host);
    } catch (UnknownHostException e) {
      throw new IllegalStateException("Could not resolve host '" + host + "' for " + uri + ".", e);
    }
    for (InetAddress address : addresses) {
      if (isPrivate(address) && !(allowLoopback && address.isLoopbackAddress())) {
        throw new IllegalArgumentException("Refused '" + uri + "': host '" + host + "' resolves to a private "
            + "or loopback address (" + address.getHostAddress() + "). Only public hosts are fetched.");
      }
    }
  }

  static boolean isPrivate(InetAddress address) {
    if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
        || address.isAnyLocalAddress() || address.isMulticastAddress()) {
      return true;
    }
    byte[] bytes = address.getAddress();
    if (bytes.length == 4) {
      return bytes[0] == 0
          || (bytes[0] == 100 && (bytes[1] & CARRIER_GRADE_NAT_SECOND_OCTET_MASK) == CARRIER_GRADE_NAT_SECOND_OCTET);
    }
    return (bytes[0] & UNIQUE_LOCAL_MASK) == UNIQUE_LOCAL_PREFIX;
  }
}
