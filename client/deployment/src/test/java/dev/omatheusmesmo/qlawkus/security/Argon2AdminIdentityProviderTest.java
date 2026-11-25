package dev.omatheusmesmo.qlawkus.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.config.AdminAuthConfig;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Argon2AdminIdentityProvider}'s credential check and setup-mode wiring, without
 * booting Quarkus. The stored hash is an Argon2id PHC hash of {@code keystore-admin-pw}.
 */
class Argon2AdminIdentityProviderTest {

  private static final String PASSWORD = "keystore-admin-pw";
  private static final String HASH =
      "$argon2id$v=19$m=19456,t=2,p=1$yzYqP90NuDfJIyRArJPy3w$wXm04u2YNdcQF6JYgDtxrJlzmcRjgW6cQ+vvUUb681M";

  @Test
  void acceptsTheRightUsernameAndPassword() {
    Argon2AdminIdentityProvider provider = provider("qlawkus", "admin", Optional.of(HASH));
    assertTrue(provider.verify("qlawkus", PASSWORD.toCharArray()));
  }

  @Test
  void rejectsWrongPasswordAndWrongUsername() {
    Argon2AdminIdentityProvider provider = provider("qlawkus", "admin", Optional.of(HASH));
    assertFalse(provider.verify("qlawkus", "wrong-password".toCharArray()));
    assertFalse(provider.verify("intruder", PASSWORD.toCharArray()));
  }

  @Test
  void withoutAHashItStartsInSetupModeAndRejectsEveryone() {
    for (Optional<String> hash : new Optional[] {Optional.empty(), Optional.of("   ")}) {
      Argon2AdminIdentityProvider provider = provider("qlawkus", "admin", hash);
      assertFalse(provider.verify("qlawkus", PASSWORD.toCharArray()));
      assertFalse(provider.verify("qlawkus", new char[0]));
    }
  }

  @Test
  void aPasswordSetDuringSetupAuthenticatesWithoutARestart() {
    AdminCredential credential = credential(Optional.empty());
    Argon2AdminIdentityProvider provider = new Argon2AdminIdentityProvider(config("qlawkus", "admin", Optional.empty()),
        credential);
    assertFalse(provider.verify("qlawkus", PASSWORD.toCharArray()));
    credential.initialize(HASH);
    assertTrue(provider.verify("qlawkus", PASSWORD.toCharArray()));
    assertFalse(provider.verify("intruder", PASSWORD.toCharArray()));
  }

  private static Argon2AdminIdentityProvider provider(String username, String role, Optional<String> hash) {
    AdminAuthConfig config = config(username, role, hash);
    return new Argon2AdminIdentityProvider(config, new AdminCredential(config));
  }

  private static AdminCredential credential(Optional<String> hash) {
    return new AdminCredential(config("qlawkus", "admin", hash));
  }

  private static AdminAuthConfig config(String username, String role, Optional<String> hash) {
    return new AdminAuthConfig() {
      @Override
      public String username() {
        return username;
      }

      @Override
      public String role() {
        return role;
      }

      @Override
      public Optional<String> passwordHash() {
        return hash;
      }

      @Override
      public Argon2 argon2() {
        return () -> true;
      }
    };
  }
}
