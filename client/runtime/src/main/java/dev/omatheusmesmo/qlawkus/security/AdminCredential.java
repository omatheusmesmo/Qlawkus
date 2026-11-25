package dev.omatheusmesmo.qlawkus.security;

import dev.omatheusmesmo.qlawkus.config.AdminAuthConfig;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Holds the admin password hash the {@link Argon2AdminIdentityProvider} verifies against, so a first boot
 * with no configured hash can start in setup mode and take its password later without a restart.
 *
 * <p>The hash comes from {@code qlawkus.admin.password-hash} (environment or keystore). When none is
 * configured nothing authenticates and {@link #setupRequired()} is true until {@link #initialize} runs
 * once. A configured credential can never be replaced through here: changing a password that already
 * exists is a different, authenticated operation.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.admin.argon2.enabled", stringValue = "true")
public class AdminCredential {

  private final Argon2idPasswordHasher hasher = new Argon2idPasswordHasher();
  private volatile String hash;

  @Inject
  public AdminCredential(AdminAuthConfig config) {
    this.hash = config.passwordHash().filter(value -> !value.isBlank()).orElse(null);
  }

  /** Whether no admin password exists yet, so the one-time setup is still open. */
  public boolean setupRequired() {
    return hash == null;
  }

  /** Whether {@code password} is the admin password. Always false while setup is required. */
  public boolean matches(char[] password) {
    String current = hash;
    return current != null && hasher.matches(password, current);
  }

  /**
   * Activates {@code newHash} as the admin credential and closes setup.
   *
   * @throws IllegalStateException when a credential already exists
   */
  synchronized void initialize(String newHash) {
    if (hash != null) {
      throw new IllegalStateException("The admin password is already set");
    }
    this.hash = newHash;
  }
}
