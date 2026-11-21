package dev.omatheusmesmo.qlawkus.tools.mcp;

import java.net.InetAddress;

/** Local cleartext exceptions never consult DNS or accept a hostname that merely resolves locally. */
public final class McpEndpointSecurity {
    private McpEndpointSecurity() {}

    public static boolean isLoopback(String host) {
        if (host == null) return false;
        if (host.equalsIgnoreCase("localhost")) return true;
        String literal = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        try {
            return InetAddress.ofLiteral(literal).isLoopbackAddress();
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }
}
