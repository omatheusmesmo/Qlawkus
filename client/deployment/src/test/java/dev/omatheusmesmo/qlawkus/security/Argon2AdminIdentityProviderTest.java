package dev.omatheusmesmo.qlawkus.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.config.AdminAuthConfig;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Argon2AdminIdentityProvider}'s credential check and fail-closed wiring, without
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
  void refusesToStartWithoutAHash() {
    assertThrows(IllegalStateException.class, () -> provider("qlawkus", "admin", Optional.empty()));
    assertThrows(IllegalStateException.class, () -> provider("qlawkus", "admin", Optional.of("   ")));
  }

  private static Argon2AdminIdentityProvider provider(String username, String role, Optional<String> hash) {
    return new Argon2AdminIdentityProvider(new AdminAuthConfig() {
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
    });
  }
}
