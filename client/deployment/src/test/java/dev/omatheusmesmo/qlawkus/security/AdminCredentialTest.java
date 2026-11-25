package dev.omatheusmesmo.qlawkus.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.config.AdminAuthConfig;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AdminCredential}: the live holder of the admin hash that lets a first boot
 * with no configured hash start in setup mode and take its password afterwards, without a restart.
 */
class AdminCredentialTest {

  private static final String PASSWORD = "keystore-admin-pw";
  private static final String HASH =
      "$argon2id$v=19$m=19456,t=2,p=1$yzYqP90NuDfJIyRArJPy3w$wXm04u2YNdcQF6JYgDtxrJlzmcRjgW6cQ+vvUUb681M";

  @Test
  void aConfiguredHashMeansNoSetupAndVerifiesThePassword() {
    AdminCredential credential = credential(Optional.of(HASH));
    assertFalse(credential.setupRequired());
    assertTrue(credential.matches(PASSWORD.toCharArray()));
    assertFalse(credential.matches("wrong-password".toCharArray()));
  }

  @Test
  void aMissingOrBlankHashMeansSetupAndNothingAuthenticates() {
    for (Optional<String> hash : new Optional[] {Optional.empty(), Optional.of("   ")}) {
      AdminCredential credential = credential(hash);
      assertTrue(credential.setupRequired());
      assertFalse(credential.matches(PASSWORD.toCharArray()));
      assertFalse(credential.matches(new char[0]));
    }
  }

  @Test
  void initializingTakesEffectAtOnceAndClosesSetup() {
    AdminCredential credential = credential(Optional.empty());
    credential.initialize(HASH);
    assertFalse(credential.setupRequired());
    assertTrue(credential.matches(PASSWORD.toCharArray()));
  }

  @Test
  void aSecondInitializationIsRefusedAndKeepsTheFirstPassword() {
    AdminCredential credential = credential(Optional.empty());
    credential.initialize(HASH);
    assertThrows(IllegalStateException.class, () -> credential.initialize(new Argon2idPasswordHasher()
        .hash("another-password".toCharArray())));
    assertTrue(credential.matches(PASSWORD.toCharArray()));
  }

  @Test
  void aConfiguredCredentialCannotBeReplacedThroughSetup() {
    AdminCredential credential = credential(Optional.of(HASH));
    assertThrows(IllegalStateException.class, () -> credential.initialize(HASH));
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
}
