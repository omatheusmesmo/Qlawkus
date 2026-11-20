package dev.omatheusmesmo.qlawkus.console.voice;

import java.net.URI;
import java.util.Collection;
import java.util.Locale;

/**
 * Decides whether a WebSocket upgrade's {@code Origin} may open a voice session. A browser always
 * sends the origin of the page that opened the socket, so requiring it to match the request's own
 * host keeps a page on another site from driving the owner's microphone, even when the owner's
 * credentials are cached by the browser. An origin listed explicitly is accepted as configured.
 */
final class VoiceOriginPolicy {

    private VoiceOriginPolicy() {
    }

    static boolean allowed(String origin, String host, Collection<String> allowlist) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        String trimmed = origin.trim();
        if (allowlist.contains(trimmed)) {
            return true;
        }
        if (host == null || host.isBlank()) {
            return false;
        }
        return sameAuthority(trimmed, host.trim());
    }

    private static boolean sameAuthority(String origin, String host) {
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !isWebScheme(scheme) || uri.getRawAuthority() == null) {
            return false;
        }
        return uri.getRawAuthority().equalsIgnoreCase(host);
    }

    private static boolean isWebScheme(String scheme) {
        String lower = scheme.toLowerCase(Locale.ROOT);
        return lower.equals("http") || lower.equals("https");
    }
}
