package dev.omatheusmesmo.qlawkus.security;

import dev.omatheusmesmo.qlawkus.config.AdminAuthConfig;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.Startup;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.credential.PasswordCredential;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Verifies the single admin credential with Argon2id, database-free, so the bundled distribution can
 * store a GPU-resistant password hash instead of a plaintext or a fast digest. It is the Qlawkus
 * baseline auth; stronger or federated auth is a {@code quarkus-oidc}/JWT drop-in (the endpoints are
 * {@code @Authenticated}), not a change here.
 *
 * <p>Gated by the build flag {@code qlawkus.admin.argon2.enabled}: off by default, so a consumer using
 * a different realm (for example the embedded properties realm in the integration tests) is untouched.
 * When on, the PHC hash is mandatory and read eagerly at {@link Startup}, so a missing credential fails
 * the boot rather than silently rejecting every request.
 */
@Startup
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.admin.argon2.enabled", stringValue = "true")
public class Argon2AdminIdentityProvider
    implements IdentityProvider<UsernamePasswordAuthenticationRequest> {

  private final String username;
  private final String role;
  private final String passwordHash;
  private final Argon2idPasswordHasher hasher = new Argon2idPasswordHasher();

  @Inject
  public Argon2AdminIdentityProvider(AdminAuthConfig config) {
    this.username = config.username();
    this.role = config.role();
    this.passwordHash = config.passwordHash()
        .filter(hash -> !hash.isBlank())
        .orElseThrow(() -> new IllegalStateException(
            "qlawkus.admin.argon2.enabled is true but qlawkus.admin.password-hash is not set; provide the "
                + "Argon2id hash via the keystore alias qlawkus.admin.password-hash or the environment "
                + "variable QLAWKUS_ADMIN_PASSWORD_HASH"));
  }

  @Override
  public Class<UsernamePasswordAuthenticationRequest> getRequestType() {
    return UsernamePasswordAuthenticationRequest.class;
  }

  @Override
  public Uni<SecurityIdentity> authenticate(UsernamePasswordAuthenticationRequest request,
      AuthenticationRequestContext context) {
    return context.runBlocking(() -> {
      char[] password = ((PasswordCredential) request.getPassword()).getPassword();
      if (!verify(request.getUsername(), password)) {
        throw new AuthenticationFailedException();
      }
      return QuarkusSecurityIdentity.builder()
          .setPrincipal(new QuarkusPrincipal(username))
          .addRole(role)
          .build();
    });
  }

  boolean verify(String suppliedUsername, char[] suppliedPassword) {
    if (!username.equals(suppliedUsername)) {
      return false;
    }
    return hasher.matches(suppliedPassword, passwordHash);
  }
}
