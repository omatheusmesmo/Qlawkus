package dev.omatheusmesmo.qlawkus.tools.mcp.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretWriter;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpConfig;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpConnection;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpEndpointPolicy;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpRegistryException;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;
import io.quarkus.oidc.client.OidcClientException;
import io.quarkus.oidc.client.Tokens;
import io.quarkus.oidc.client.runtime.OidcClientConfig;
import io.quarkus.oidc.common.runtime.OidcCommonUtils;
import io.quarkus.oidc.common.runtime.OidcConstants;
import io.quarkus.oidc.common.runtime.config.OidcClientCommonConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Authorizes an external MCP resource independently of the console's own login. Quarkus owns token
 * requests, refresh and revocation; this class owns the bounded browser state/PKCE handoff and vault
 * persistence. No token value is returned by an admin or model-facing method.
 */
@ApplicationScoped
public class McpOAuthService {
    private static final Duration LOGIN_LIFETIME = Duration.ofMinutes(10);
    private final OidcClients clients;
    private final KeystoreSecretWriter vault;
    private final ObjectMapper json;
    private final Duration timeout;
    private final McpEndpointPolicy endpoints;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final Object[] tokenLocks = new Object[64];

    @Inject
    public McpOAuthService(OidcClients clients, KeystoreSecretWriter vault, ObjectMapper json, McpConfig config) {
        this.clients = clients;
        this.vault = vault;
        this.json = json;
        this.timeout = config.discoveryTimeout();
        this.endpoints = new McpEndpointPolicy(config.allowPrivateEndpoints());
        for (int i = 0; i < tokenLocks.length; i++) tokenLocks[i] = new Object();
    }

    /** Does not contact the authorization server. */
    public boolean isAuthorized(McpConnection connection) {
        if (connection.oauth() == null) return true;
        try {
            McpOAuthValidator.validate(connection);
            if (connection.oauth().grant() == McpOAuthGrant.CLIENT_CREDENTIALS) {
                return vault.readSecret(connection.oauth().clientSecretRef()).filter(value -> !value.isBlank()).isPresent();
            }
            Bundle bundle = read(connection);
            return bundle != null && (bundle.expiresAt > now() + 5 || bundle.refreshToken != null);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    public AuthStatus status(McpConnection connection) {
        if (connection.oauth() == null) return new AuthStatus("NONE", true, false);
        boolean authorized = isAuthorized(connection);
        Bundle bundle = readQuietly(connection);
        return new AuthStatus(authorized ? "READY" : "AUTH_REQUIRED", authorized,
                bundle != null && bundle.refreshToken != null);
    }

    public LoginStart begin(McpConnection connection, String owner) {
        McpOAuthValidator.validate(connection);
        require(connection.oauth() != null && connection.oauth().grant() == McpOAuthGrant.AUTHORIZATION_CODE,
                "This connection does not use browser authorization");
        synchronized (pending) {
            pending.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(Instant.now()));
            pending.entrySet().removeIf(entry -> entry.getValue().connectionId.equals(connection.id()));
            require(pending.size() < 32, "Too many pending OAuth authorizations");
            String state = random();
            String verifier = random();
            Instant expires = Instant.now().plus(LOGIN_LIFETIME);
            pending.put(state, new Pending(connection.id(), fingerprint(connection), owner, verifier, expires));
            McpOAuth oauth = connection.oauth();
            Map<String, String> parameters = new LinkedHashMap<>();
            parameters.put(OidcConstants.CODE_FLOW_RESPONSE_TYPE, OidcConstants.CODE_FLOW_CODE);
            parameters.put(OidcConstants.CLIENT_ID, oauth.clientId());
            parameters.put(OidcConstants.CODE_FLOW_REDIRECT_URI, oauth.redirectUri());
            parameters.put(OidcConstants.CODE_FLOW_STATE, state);
            parameters.put(OidcConstants.PKCE_CODE_CHALLENGE, OidcCommonUtils.base64UrlEncode(digest(verifier)));
            parameters.put(OidcConstants.PKCE_CODE_CHALLENGE_METHOD, OidcConstants.PKCE_CODE_CHALLENGE_S256);
            if (!oauth.scopes().isEmpty()) parameters.put(OidcConstants.TOKEN_SCOPE, String.join(" ", oauth.scopes()));
            if (oauth.resource() != null) parameters.put("resource", oauth.resource());
            String query = parameters.entrySet().stream().map(entry -> OidcCommonUtils.urlEncode(entry.getKey()) + "=" + OidcCommonUtils.urlEncode(entry.getValue()))
                    .collect(Collectors.joining("&"));
            return new LoginStart(oauth.authorizationEndpoint() + "?" + query, expires);
        }
    }

    /** Resolves the callback's connection; complete consumes the state before token exchange. */
    public String connectionForState(String state) {
        require(state != null && state.length() <= 128, "Invalid or expired OAuth authorization");
        Pending value = pending.get(state);
        require(value != null && value.expiresAt.isAfter(Instant.now()), "Invalid or expired OAuth authorization");
        return value.connectionId;
    }

    public void complete(String state, String code, String owner, McpConnection connection) {
        require(state != null && state.length() <= 128, "Invalid or expired OAuth authorization");
        Pending authorization = pending.remove(state);
        require(authorization != null && authorization.expiresAt.isAfter(Instant.now())
                && authorization.connectionId.equals(connection.id())
                && authorization.fingerprint.equals(fingerprint(connection))
                && (authorization.owner == null || Objects.equals(authorization.owner, owner)),
                "Invalid or expired OAuth authorization");
        require(code != null && !code.isBlank() && code.length() <= 8192
                && code.chars().noneMatch(Character::isISOControl), "Invalid OAuth authorization code");
        synchronized (lock(connection.id())) {
            try (OidcClient client = client(connection)) {
                Map<String, String> parameters = grantParameters(connection);
                parameters.put(OidcConstants.CODE_FLOW_CODE, code);
                parameters.put(OidcConstants.CODE_FLOW_REDIRECT_URI, connection.oauth().redirectUri());
                parameters.put(OidcConstants.PKCE_CODE_VERIFIER, authorization.verifier);
                store(connection, client.getTokens(parameters).await().atMost(timeout), null);
            } catch (Exception failure) {
                throw new McpRegistryException("OAuth authorization could not be completed");
            }
        }
    }

    /** For the transport only. Renewal uses upstream OidcClient, never a model-visible tool. */
    public String authorizationHeader(McpConnection connection) {
        if (connection.oauth() == null) return null;
        synchronized (lock(connection.id())) {
            try {
                Bundle bundle = read(connection);
                if (bundle != null && bundle.expiresAt > now() + 5) return "Bearer " + bundle.accessToken;
                McpOAuth oauth = connection.oauth();
                require(bundle != null && bundle.refreshToken != null
                        || oauth.grant() == McpOAuthGrant.CLIENT_CREDENTIALS, "MCP OAuth authorization is required");
                try (OidcClient client = client(connection)) {
                    Tokens tokens = bundle != null && bundle.refreshToken != null
                            ? client.refreshTokens(bundle.refreshToken, grantParameters(connection)).await().atMost(timeout)
                            : client.getTokens(grantParameters(connection)).await().atMost(timeout);
                    Bundle renewed = store(connection, tokens, bundle);
                    return "Bearer " + renewed.accessToken;
                }
            } catch (Exception failure) {
                if (connection.oauth().grant() == McpOAuthGrant.AUTHORIZATION_CODE && invalidGrant(failure)) {
                    try { vault.deleteSecret(alias(connection)); } catch (RuntimeException ignored) { }
                }
                throw new McpRegistryException("MCP OAuth authorization is required or could not be renewed");
            }
        }
    }

    public DisconnectStatus disconnect(McpConnection connection) {
        Bundle previous = readQuietly(connection);
        forget(connection);
        boolean revoked = false;
        if (previous != null && connection.oauth() != null && connection.oauth().revocationEndpoint() != null) {
            try (OidcClient client = client(connection)) {
                revoked = Boolean.TRUE.equals(client.revokeAccessToken(previous.accessToken).await().atMost(timeout));
                if (previous.refreshToken != null) {
                    revoked &= Boolean.TRUE.equals(client.revokeAccessToken(previous.refreshToken,
                            Map.of(OidcConstants.REVOCATION_TOKEN_TYPE_HINT, OidcConstants.REFRESH_TOKEN_GRANT)).await().atMost(timeout));
                }
            } catch (Exception ignored) {
                revoked = false;
            }
        }
        return new DisconnectStatus(true, revoked);
    }

    /** Clears local authorization without a provider call, for connection removal. */
    public void forget(McpConnection connection) {
        synchronized (lock(connection.id())) {
            pending.entrySet().removeIf(entry -> entry.getValue().connectionId.equals(connection.id()));
            if (connection.oauth() != null) {
                try {
                    vault.deleteSecret(alias(connection));
                } catch (RuntimeException failure) {
                    throw new McpRegistryException("MCP OAuth authorization could not be removed from the vault");
                }
            }
        }
    }

    /** Internal redaction input; never expose these values to a tool, endpoint or log. */
    public List<String> secretsForRedaction(McpConnection connection) {
        if (connection.oauth() == null) return List.of();
        List<String> secrets = new ArrayList<>();
        Bundle bundle = readQuietly(connection);
        if (bundle != null) {
            secrets.add(bundle.accessToken);
            if (bundle.refreshToken != null) secrets.add(bundle.refreshToken);
        }
        if (connection.oauth().clientSecretRef() != null) {
            try { vault.readSecret(connection.oauth().clientSecretRef()).ifPresent(secrets::add); }
            catch (RuntimeException ignored) { }
        }
        return List.copyOf(secrets);
    }

    /**
     * Upstream passes the connection retry count straight to Mutiny, which rejects zero, hence one.
     */
    private OidcClient client(McpConnection connection) {
        endpoints.require(connection);
        McpOAuth oauth = connection.oauth();
        var builder = OidcClientConfig.builder().id("qlawkus-mcp-" + connection.id())
                .clientEnabled(true)
                .authServerUrl(oauth.tokenEndpoint()).discoveryEnabled(false).tokenPath(oauth.tokenEndpoint())
                .clientId(oauth.clientId()).scopes(oauth.scopes()).earlyTokensAcquisition(false)
                .connectionTimeout(timeout).connectionRetryCount(1).accessTokenExpiresIn(Duration.ofMinutes(1))
                .grant(oauth.grant() == McpOAuthGrant.CLIENT_CREDENTIALS
                        ? OidcClientConfig.Grant.Type.CLIENT : OidcClientConfig.Grant.Type.CODE);
        if (oauth.revocationEndpoint() != null) builder.revokePath(oauth.revocationEndpoint());
        if (oauth.clientSecretRef() != null) {
            String secret = vault.readSecret(oauth.clientSecretRef())
                    .orElseThrow(() -> new McpRegistryException("OAuth client secret is unavailable"));
            require(!secret.isBlank() && secret.length() <= 8192 && secret.chars().noneMatch(Character::isISOControl),
                    "Invalid OAuth client secret");
            builder.credentials().clientSecret(secret, oauth.clientAuthMethod() == McpOAuthClientAuthMethod.BASIC
                    ? OidcClientCommonConfig.Credentials.Secret.Method.BASIC
                    : OidcClientCommonConfig.Credentials.Secret.Method.POST).end();
        }
        return clients.newClient(builder.build()).await().atMost(timeout);
    }

    private Map<String, String> grantParameters(McpConnection connection) {
        Map<String, String> parameters = new HashMap<>();
        if (connection.oauth().resource() != null) parameters.put("resource", connection.oauth().resource());
        return parameters;
    }

    /**
     * The provider's JSON error body travels in the exception message. Only its standard error code is
     * read, within a size bound, and neither the body nor its description is logged or returned. A
     * rejected grant needs new consent, while a temporary outage keeps the refresh grant.
     */
    private boolean invalidGrant(Throwable failure) {
        for (int i = 0; failure != null && i < 4; i++, failure = failure.getCause()) {
            if (failure instanceof OidcClientException && failure.getMessage() != null && failure.getMessage().length() <= 32768) {
                try {
                    String code = json.readTree(failure.getMessage()).path("error").asString();
                    if (code.equals("invalid_grant") || code.equals("invalid_token")) return true;
                } catch (Exception ignored) { }
            }
        }
        return false;
    }

    private Bundle readQuietly(McpConnection connection) {
        try { return read(connection); } catch (RuntimeException failure) { return null; }
    }

    private Bundle read(McpConnection connection) {
        if (connection.oauth() == null) return null;
        String value = vault.readSecret(alias(connection)).orElse(null);
        if (value == null) return null;
        try {
            require(value.length() <= 32768, "Invalid OAuth token bundle");
            JsonNode node = json.readTree(value);
            if (!fingerprint(connection).equals(node.path("connection_hash").asString())) return null;
            String access = token(node.path("access_token").asString(null));
            String refresh = node.path("refresh_token").isTextual() ? token(node.path("refresh_token").asString()) : null;
            return new Bundle(access, refresh, node.path("expires_at").asLong(0));
        } catch (Exception failure) {
            throw new McpRegistryException("Invalid OAuth authorization in vault");
        }
    }

    private Bundle store(McpConnection connection, Tokens tokens, Bundle previous) {
        String access = token(tokens.getAccessToken());
        String refresh = tokens.getRefreshToken() == null ? previous == null ? null : previous.refreshToken
                : token(tokens.getRefreshToken());
        long expires = tokens.getAccessTokenExpiresAt() == null ? now() + 60 : tokens.getAccessTokenExpiresAt();
        require(expires > now() && expires - now() <= 366L * 24 * 60 * 60, "Invalid OAuth token expiration");
        ObjectNode node = json.createObjectNode();
        node.put("connection_hash", fingerprint(connection));
        node.put("access_token", access);
        if (refresh != null) node.put("refresh_token", refresh);
        node.put("expires_at", expires);
        vault.setSecret(alias(connection), node.toString());
        return new Bundle(access, refresh, expires);
    }

    private String fingerprint(McpConnection connection) {
        try {
            return HexFormat.of().formatHex(digest(json.writeValueAsString(List.of(connection.id(), connection.url(), connection.oauth()))));
        } catch (Exception failure) {
            throw new McpRegistryException("Invalid OAuth connection metadata");
        }
    }

    private static String token(String value) {
        require(value != null && !value.isBlank() && value.length() <= 8192
                && value.chars().noneMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c)),
                "Invalid OAuth token response");
        return value;
    }

    private Object lock(String id) { return tokenLocks[(id.hashCode() & Integer.MAX_VALUE) % tokenLocks.length]; }
    private static String alias(McpConnection connection) { return "qlawkus.mcp.servers." + connection.id() + ".oauth.tokens"; }
    private static long now() { return Instant.now().getEpochSecond(); }
    private static String random() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return OidcCommonUtils.base64UrlEncode(bytes); }
    private static byte[] digest(String text) {
        try { return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception failure) { throw new McpRegistryException("OAuth digest is unavailable"); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new McpRegistryException(message); }

    private record Pending(String connectionId, String fingerprint, String owner, String verifier, Instant expiresAt) {}
    private record Bundle(String accessToken, String refreshToken, long expiresAt) {}
    public record LoginStart(String authorizationUrl, Instant expiresAt) {}
    public record AuthStatus(String state, boolean authorized, boolean refreshAvailable) {}
    public record DisconnectStatus(boolean disconnected, boolean remoteRevoked) {}
}
