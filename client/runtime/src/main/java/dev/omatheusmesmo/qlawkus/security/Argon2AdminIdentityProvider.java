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
 * With no hash configured the app boots in setup mode, where every request is rejected until the first-run
 * setup ({@link AdminSetupService}) stores a password: it stays fail-closed, but starts so the owner can
 * finish the setup from a browser instead of editing environment files.
 */
@Startup
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.admin.argon2.enabled", stringValue = "true")
public class Argon2AdminIdentityProvider
    implements IdentityProvider<UsernamePasswordAuthenticationRequest> {

  private final String username;
  private final String role;
  private final AdminCredential credential;

  @Inject
  public Argon2AdminIdentityProvider(AdminAuthConfig config, AdminCredential credential) {
    this.username = config.username();
    this.role = config.role();
    this.credential = credential;
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
    return credential.matches(suppliedPassword);
  }
}
