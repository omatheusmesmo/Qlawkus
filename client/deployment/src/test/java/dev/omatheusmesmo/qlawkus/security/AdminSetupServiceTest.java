package dev.omatheusmesmo.qlawkus.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.config.AdminAuthConfig;
import dev.omatheusmesmo.qlawkus.config.SecretsConfig;
import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link AdminSetupService}: a first boot without an admin hash accepts exactly one
 * password, only with the one-time token printed in the log, stores only its Argon2id hash and applies
 * it without a restart. The keystore password is not configured here, so these also run the
 * generated-password path.
 */
class AdminSetupServiceTest {

  private static final String PASSWORD = "a-long-enough-password";

  @TempDir
  Path directory;

  @Test
  void theTokenIsLongAndDiffersBetweenBoots() {
    AdminSetupService first = service(emptyCredential());
    AdminSetupService second = service(emptyCredential());
    assertTrue(first.token().length() >= 32);
    assertNotEquals(first.token(), second.token());
  }

  @Test
  void completingWithTheTokenStoresTheHashAndActivatesThePassword() {
    AdminCredential credential = emptyCredential();
    KeystoreSecretWriter writer = writer();
    AdminSetupService service = new AdminSetupService(credential, writer);

    service.complete(service.token(), PASSWORD.toCharArray());

    assertFalse(credential.setupRequired());
    assertTrue(credential.matches(PASSWORD.toCharArray()));
    String stored = writer.readSecret(AdminSetupService.HASH_ALIAS).orElseThrow();
    assertTrue(stored.startsWith("$argon2id$"));
    assertFalse(stored.contains(PASSWORD));
  }

  @Test
  void aWrongOrMissingTokenIsRefusedAndChangesNothing() {
    AdminCredential credential = emptyCredential();
    KeystoreSecretWriter writer = writer();
    AdminSetupService service = new AdminSetupService(credential, writer);

    assertThrows(AdminSetupService.InvalidTokenException.class,
        () -> service.complete("not-the-token", PASSWORD.toCharArray()));
    assertThrows(AdminSetupService.InvalidTokenException.class,
        () -> service.complete(null, PASSWORD.toCharArray()));
    assertTrue(credential.setupRequired());
    assertTrue(writer.readSecret(AdminSetupService.HASH_ALIAS).isEmpty());
  }

  @Test
  void aShortPasswordIsRefusedEvenWithTheRightToken() {
    AdminCredential credential = emptyCredential();
    AdminSetupService service = new AdminSetupService(credential, writer());

    assertThrows(IllegalArgumentException.class,
        () -> service.complete(service.token(), "short".toCharArray()));
    assertThrows(IllegalArgumentException.class,
        () -> service.complete(service.token(), null));
    assertTrue(credential.setupRequired());
  }

  @Test
  void aPasswordTooLongToHashCheaplyIsRefused() {
    AdminSetupService service = service(emptyCredential());
    assertThrows(IllegalArgumentException.class,
        () -> service.complete(service.token(), "x".repeat(AdminSetupService.MAX_PASSWORD_LENGTH + 1).toCharArray()));
  }

  @Test
  void onlyTheFirstCompletionSucceeds() {
    AdminCredential credential = emptyCredential();
    AdminSetupService service = new AdminSetupService(credential, writer());
    service.complete(service.token(), PASSWORD.toCharArray());

    assertThrows(AdminSetupService.SetupClosedException.class,
        () -> service.complete(service.token(), "another-long-password".toCharArray()));
    assertTrue(credential.matches(PASSWORD.toCharArray()));
  }

  @Test
  void aConfiguredCredentialMeansSetupIsClosedFromTheStart() {
    AdminCredential configured = credential(Optional.of(
        "$argon2id$v=19$m=19456,t=2,p=1$yzYqP90NuDfJIyRArJPy3w$wXm04u2YNdcQF6JYgDtxrJlzmcRjgW6cQ+vvUUb681M"));
    AdminSetupService service = new AdminSetupService(configured, writer());
    assertFalse(service.required());
    assertThrows(AdminSetupService.SetupClosedException.class,
        () -> service.complete(service.token(), PASSWORD.toCharArray()));
  }

  @Test
  void anUnwritableKeystoreLeavesSetupOpenInsteadOfLosingThePasswordOnRestart() {
    AdminCredential credential = emptyCredential();
    Path blocker = directory.resolve("file-not-a-directory");
    try {
      Files.writeString(blocker, "x");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    KeystoreSecretWriter writer = new KeystoreSecretWriter(config(blocker.resolve("secrets.p12"), Optional.empty()));
    AdminSetupService service = new AdminSetupService(credential, writer);

    assertThrows(AdminSetupService.NotPersistedException.class,
        () -> service.complete(service.token(), PASSWORD.toCharArray()));
    assertTrue(credential.setupRequired());
    assertEquals(true, service.required());
  }

  private AdminSetupService service(AdminCredential credential) {
    return new AdminSetupService(credential, writer());
  }

  private KeystoreSecretWriter writer() {
    return new KeystoreSecretWriter(config(directory.resolve("secrets.p12"), Optional.empty()));
  }

  private static AdminCredential emptyCredential() {
    return credential(Optional.empty());
  }

  private static AdminCredential credential(Optional<String> hash) {
    return new AdminCredential(new AdminAuthConfig() {
      @Override
      public String username() {
        return "qlawkus";
      }

      @Override
      public String role() {
        return "admin";
      }

      @Override
      public Optional<String> passwordHash() {
        return hash;
      }

      @Override
      public Argon2 argon2() {
        return () -> true;
      }
    });
  }

  private static SecretsConfig config(Path keystore, Optional<String> password) {
    return new SecretsConfig() {
      @Override
      public String keystorePath() {
        return keystore.toString();
      }

      @Override
      public Optional<String> keystorePassword() {
        return password;
      }

      @Override
      public int keystoreOrdinal() {
        return 350;
      }
    };
  }
}
