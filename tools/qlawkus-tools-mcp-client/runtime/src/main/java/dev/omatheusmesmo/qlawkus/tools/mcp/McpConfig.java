package dev.omatheusmesmo.qlawkus.tools.mcp;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.quarkus.runtime.annotations.ConfigDocIgnore;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuthGrant;
import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuthClientAuthMethod;

/** Runtime MCP bootstrap connections and global resource budgets. */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.mcp")
public interface McpConfig {
  /** Bootstrap connections, used until a managed registry snapshot exists. */
  Map<String, Server> servers();
  /** Maximum number of configured connections. */
  @WithDefault("16") @Min(1) @Max(64) int maxServers();
  /** Maximum tools accepted from one connection. Larger catalogs are refused. */
  @WithDefault("32") @Min(1) @Max(128) int maxToolsPerServer();
  /** Maximum contributed tools across all connections. */
  @WithDefault("128") @Min(1) @Max(512) int maxTools();
  /** Cooperative total connection initialization and discovery budget. */
  @WithDefault("5S") @DurationMin(millis = 1) @DurationMax(seconds = 60) Duration discoveryTimeout();
  /** Deadline for one remote tool execution. */
  @WithDefault("30S") @DurationMin(millis = 1) @DurationMax(seconds = 300) Duration callTimeout();
  /** Maximum characters returned by one remote tool. */
  @WithDefault("16000") @Min(128) @Max(1000000) int maxOutputChars();
  /** Delay before retrying unavailable bootstrap connections. */
  @WithDefault("30S") @DurationMin(millis = 1) @DurationMax(seconds = 3600) Duration retryBackoff();
  /**
   * Whether HTTP and OAuth endpoints may be, or resolve to, loopback, private, link-local or other
   * non-public addresses. Off by default so a connection cannot reach the host's own network or a
   * cloud metadata service; turn it on for MCP servers that run beside the agent.
   */
  @WithDefault("false") boolean allowPrivateEndpoints();
  /**
   * Approval for connection changes the agent proposes through its tools. {@code hitl} makes the
   * agent preview the change and wait for the owner's go-ahead; {@code yolo} applies it at once.
   * The console and admin API are the owner acting directly and never ask.
   */
  @WithDefault("hitl") ApprovalMode approvalMode();

  /** How the agent obtains the owner's go-ahead before changing a connection. */
  enum ApprovalMode {
    /** Apply the change immediately. */
    YOLO,
    /** Preview first and require a confirmed re-call. */
    HITL
  }

  /** An operator bootstrap connection; credential values come from server-scoped vault aliases. */
  interface Server {
    /** Upstream transport: HTTP (streamable HTTP) or STDIO. */
    @WithDefault("HTTP") McpTransport transport();
    /** Whether this connection participates in discovery. */
    @WithDefault("true") boolean enabled();
    /** HTTP endpoint without credentials, query string or fragment. */
    Optional<String> url();
    /** STDIO executable and separate arguments; no shell expansion is performed. */
    Optional<List<String>> command();
    /** HTTP header name to server-scoped vault alias mapping. */
    Map<String, String> headerSecretRefs();
    /** STDIO variable name to server-scoped vault alias mapping. */
    Map<String, String> environmentSecretRefs();
    /** Credential aliases populated by the vault ConfigSource; not used as registry metadata. */
    @ConfigDocIgnore Map<String, String> headers();
    /** Credential aliases populated by the vault ConfigSource; not used as registry metadata. */
    @ConfigDocIgnore Map<String, String> environment();
    /** Optional protocol version; unset delegates version negotiation to the upstream client. */
    Optional<String> protocolVersion();
    /** Optional OAuth credentials or owner authorization, selected by its grant property. */
    OAuth oauth();
  }

  /** OAuth endpoint metadata. No secret value is included in the connection registry. */
  interface OAuth {
    /** Grant used for machine credentials or interactive owner authorization. */
    Optional<McpOAuthGrant> grant();
    /** Explicit token endpoint, including for OAuth-only services without OIDC discovery. */
    Optional<String> tokenEndpoint();
    /** Interactive authorization endpoint; required for authorization-code grants. */
    Optional<String> authorizationEndpoint();
    /** Optional token revocation endpoint. */
    Optional<String> revocationEndpoint();
    /** Registered OAuth client identifier. */
    Optional<String> clientId();
    /** Server-scoped vault alias for a confidential client's secret. */
    Optional<String> clientSecretRef();
    /** Confidential client authentication at the token endpoint: POST or BASIC. */
    @WithDefault("POST") McpOAuthClientAuthMethod clientAuthMethod();
    /** Requested scopes. */
    Optional<List<String>> scopes();
    /** Fixed application callback URI ending in /api/admin/mcp/oauth/callback. */
    Optional<String> redirectUri();
    /** Optional OAuth resource indicator for the MCP resource. */
    Optional<String> resource();
    /** Live vault credential; excluded from configuration previews. */
    @ConfigDocIgnore Optional<String> clientSecret();
    /** Live vault token bundle; excluded from configuration previews. */
    @ConfigDocIgnore Optional<String> tokens();
  }
}
