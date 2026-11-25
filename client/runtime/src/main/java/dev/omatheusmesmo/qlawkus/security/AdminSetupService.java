package dev.omatheusmesmo.qlawkus.security;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * The one-time first-run setup of the admin password, for a deployment that starts with none configured.
 *
 * <p>Setup is open only while {@link AdminCredential#setupRequired()} holds, and only a caller that knows
 * the setup token can use it. The token is random, held in memory and printed once to the log at boot, so
 * it proves access to the container's log and not merely to its network port. The password is hashed with
 * Argon2id and only the hash is stored (in the keystore, under {@link #HASH_ALIAS}, so it survives a
 * restart) before it takes effect. A password that cannot be persisted is refused, since accepting it
 * would silently lose it at the next restart.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.admin.argon2.enabled", stringValue = "true")
public class AdminSetupService {

  /** Keystore alias of the admin hash, the same one a configured deployment supplies by hand. */
  public static final String HASH_ALIAS = "qlawkus.admin.password-hash";
  public static final int MIN_PASSWORD_LENGTH = 12;
  public static final int MAX_PASSWORD_LENGTH = 128;
  private static final int TOKEN_BYTES = 24;

  private final AdminCredential credential;
  private final KeystoreSecretWriter keystore;
  private final String token;
  private final Argon2idPasswordHasher hasher = new Argon2idPasswordHasher();

  @Inject
  public AdminSetupService(AdminCredential credential, KeystoreSecretWriter keystore) {
    this.credential = credential;
    this.keystore = keystore;
    byte[] bytes = new byte[TOKEN_BYTES];
    new SecureRandom().nextBytes(bytes);
    this.token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  void announce(@Observes StartupEvent startup) {
    if (required()) {
      Log.warnf("First-run setup: no admin password is configured. Open /console on this agent and enter "
          + "the setup token %s to choose one. The token is valid until the password is set or the agent "
          + "restarts.", token);
    }
  }

  /** Whether the admin password has still to be chosen. */
  public boolean required() {
    return credential.setupRequired();
  }

  /**
   * The setup token, for the startup log and for tests. Never return it from an endpoint or log it again:
   * its only purpose is to prove access to the agent's log.
   */
  public String token() {
    return token;
  }

  /**
   * Sets the admin password. Wipes {@code password} before returning.
   *
   * @throws SetupClosedException when a password already exists
   * @throws InvalidTokenException when {@code suppliedToken} is not the setup token
   * @throws IllegalArgumentException when the password is missing, too short or too long
   * @throws NotPersistedException when the hash cannot be stored, leaving setup open
   */
  public synchronized void complete(String suppliedToken, char[] password) {
    try {
      if (!required()) {
        throw new SetupClosedException();
      }
      if (!tokenMatches(suppliedToken)) {
        throw new InvalidTokenException();
      }
      requireAcceptable(password);
      String hash = hasher.hash(password);
      try {
        keystore.setSecret(HASH_ALIAS, hash);
      } catch (RuntimeException failure) {
        Log.errorf("The admin password could not be stored: %s", failure.getMessage());
        throw new NotPersistedException(failure);
      }
      credential.initialize(hash);
      Log.info("The admin password was set through the first-run setup.");
    } finally {
      if (password != null) {
        Arrays.fill(password, '\0');
      }
    }
  }

  private boolean tokenMatches(String supplied) {
    return supplied != null && MessageDigest.isEqual(
        supplied.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
  }

  private static void requireAcceptable(char[] password) {
    if (password == null || password.length < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException(
          "The password must have at least " + MIN_PASSWORD_LENGTH + " characters");
    }
    if (password.length > MAX_PASSWORD_LENGTH) {
      throw new IllegalArgumentException(
          "The password must have at most " + MAX_PASSWORD_LENGTH + " characters");
    }
  }

  /** The admin password is already set, so setup is closed. */
  public static final class SetupClosedException extends RuntimeException {
    SetupClosedException() {
      super("The admin password is already set");
    }
  }

  /** The supplied token is not the setup token printed in the log. */
  public static final class InvalidTokenException extends RuntimeException {
    InvalidTokenException() {
      super("The setup token is wrong. Copy it from the agent log");
    }
  }

  /** The password could not be stored, so setup stays open and nothing changed. */
  public static final class NotPersistedException extends RuntimeException {
    NotPersistedException(Throwable cause) {
      super("The password could not be stored. Mount a writable volume at the keystore directory "
          + "(qlawkus.secrets.keystore-path) and retry", cause);
    }
  }
}
