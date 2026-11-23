package dev.omatheusmesmo.qlawkus.tools.webfetch;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides whether a URL may be fetched on the owner's behalf. The first request and every redirect hop
 * pass through {@link #check} before a connection is opened: the scheme must be http or https, the URL
 * must carry no credentials, the host must not be on the blocked list, and every address it resolves to
 * must be public.
 *
 * <p>A {@link DefaultBean} so the production rules hold unless a test profile supplies an alternative
 * that reaches a loopback test server; the protected constructor exists for that.
 */
@ApplicationScoped
@DefaultBean
public class FetchPolicy {

    private static final int FIRST_OCTET_RESERVED = 240;
    private static final int UNIQUE_LOCAL_MASK = 0xfe;
    private static final int UNIQUE_LOCAL_PREFIX = 0xfc;

    private final Set<String> blocked;
    private final boolean allowLoopback;

    @Inject
    public FetchPolicy(WebFetchConfig config) {
        this(config.blockedDomains().orElse(List.of()), false);
    }

    protected FetchPolicy(Collection<String> blockedDomains, boolean allowLoopback) {
        this.blocked = blockedDomains.stream()
                .map(FetchPolicy::normalize)
                .filter(domain -> !domain.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.allowLoopback = allowLoopback;
    }

    /**
     * Refuses {@code uri} unless it may be fetched.
     *
     * @throws FetchRefusedException with the reason when the URL is refused
     * @throws IllegalStateException when the host cannot be resolved at all
     */
    public void check(URI uri) {
        resolve(uri);
    }

    /**
     * Applies the same rules as {@link #check} and returns the addresses it validated. The caller connects
     * to one of these and to nothing it resolves again, so a name that answers differently the second time
     * (DNS rebinding) cannot lead the connection somewhere the rules never saw.
     *
     * @throws FetchRefusedException with the reason when the URL is refused
     * @throws IllegalStateException when the host cannot be resolved at all
     */
    public InetAddress[] resolve(URI uri) {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new FetchRefusedException("Refused '" + scheme + "://" + uri.getHost()
                    + "': only http:// and https:// links are fetched.");
        }
        if (uri.getUserInfo() != null) {
            throw new FetchRefusedException("Refused a URL with credentials in it (host " + uri.getHost()
                    + "). Use the plain link without user:password.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new FetchRefusedException("Refused '" + uri + "': the link has no host name.");
        }
        String name = normalize(host);
        if (isBlocked(name)) {
            throw new FetchRefusedException("Refused '" + uri + "': the domain is blocked by qlawkus.web-fetch.blocked-domains.");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(name);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Could not resolve host '" + name + "'.", e);
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address.getHostAddress())) {
                throw new FetchRefusedException("Refused '" + uri + "': the address " + address.getHostAddress()
                        + " is blocked by qlawkus.web-fetch.blocked-domains.");
            }
            if (isPrivate(address) && !(allowLoopback && address.isLoopbackAddress())) {
                throw new FetchRefusedException("Refused '" + uri + "': host '" + name + "' resolves to a private "
                        + "or loopback address (" + address.getHostAddress() + "). Only public hosts are fetched.");
            }
        }
        return addresses;
    }

    private boolean isBlocked(String name) {
        return blocked.stream().anyMatch(domain -> name.equals(domain) || (!isAddress(domain) && name.endsWith("." + domain)));
    }

    private static boolean isAddress(String entry) {
        return entry.indexOf(':') >= 0 || entry.chars().allMatch(c -> c == '.' || Character.isDigit(c));
    }

    private static String normalize(String host) {
        String lower = host.strip().toLowerCase(Locale.ROOT);
        if (lower.startsWith("[") && lower.endsWith("]")) {
            lower = lower.substring(1, lower.length() - 1);
        }
        return lower.endsWith(".") ? lower.substring(0, lower.length() - 1) : lower;
    }

    static boolean isPrivate(InetAddress address) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isReservedV4(bytes[0] & 0xff, bytes[1] & 0xff, bytes[2] & 0xff);
        }
        if ((bytes[0] & UNIQUE_LOCAL_MASK) == UNIQUE_LOCAL_PREFIX) {
            return true;
        }
        if (isNat64(bytes)) {
            return isReservedV4(bytes[12] & 0xff, bytes[13] & 0xff, bytes[14] & 0xff);
        }
        if ((bytes[0] & 0xff) == 0x20 && (bytes[1] & 0xff) == 0x02) {
            return isReservedV4(bytes[2] & 0xff, bytes[3] & 0xff, bytes[4] & 0xff);
        }
        return false;
    }

    private static boolean isReservedV4(int first, int second, int third) {
        return first == 0
                || first == 127
                || first >= FIRST_OCTET_RESERVED
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 192 && second == 0 && third == 0)
                || (first == 198 && (second == 18 || second == 19))
                || first == 10
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 169 && second == 254);
    }

    private static boolean isNat64(byte[] bytes) {
        if (bytes[0] != 0 || bytes[1] != 0x64 || (bytes[2] & 0xff) != 0xff || bytes[3] != (byte) 0x9b) {
            return false;
        }
        for (int i = 4; i < 12; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
