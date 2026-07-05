package dev.omatheusmesmo.qlawkus.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Argon2idPasswordHasher}. The decisive one is {@link #verifiesAReferenceVector}:
 * the hash was produced by the reference {@code argon2} CLI, not by this class, so accepting it proves
 * the PHC parsing and Argon2id recomputation interoperate with a standard implementation (and that the
 * cost parameters are read from the stored string, since the vector uses m=65536 while this class
 * defaults to m=19456).
 */
class Argon2idPasswordHasherTest {

  /** {@code printf 'argon2-interop-pw' | argon2 qlawkussalt123 -id -t 2 -m 16 -p 1 -l 32 -e} */
  private static final String REFERENCE_VECTOR =
      "$argon2id$v=19$m=65536,t=2,p=1$cWxhd2t1c3NhbHQxMjM$HO3ZRIfwTABvxZauCcXj2qUKI0Mdq8UmoYiPV+a9Yy0";
  private static final String REFERENCE_PASSWORD = "argon2-interop-pw";

  private final Argon2idPasswordHasher hasher = new Argon2idPasswordHasher();

  @Test
  void hashesAndVerifiesRoundTrip() {
    String hash = hasher.hash("correct horse battery staple".toCharArray());
    assertTrue(hasher.matches("correct horse battery staple".toCharArray(), hash));
    assertFalse(hasher.matches("Correct horse battery staple".toCharArray(), hash));
  }

  @Test
  void verifiesAReferenceVector() {
    assertTrue(hasher.matches(REFERENCE_PASSWORD.toCharArray(), REFERENCE_VECTOR),
        "must accept a hash produced by the reference argon2 CLI");
    assertFalse(hasher.matches("wrong-password".toCharArray(), REFERENCE_VECTOR));
  }

  @Test
  void saltIsRandomPerHash() {
    String first = hasher.hash("same-password".toCharArray());
    String second = hasher.hash("same-password".toCharArray());
    assertNotEquals(first, second, "each hash must carry its own random salt");
    assertTrue(hasher.matches("same-password".toCharArray(), first));
    assertTrue(hasher.matches("same-password".toCharArray(), second));
  }

  @Test
  void malformedHashIsNotAMatchAndDoesNotThrow() {
    assertFalse(hasher.matches("x".toCharArray(), ""));
    assertFalse(hasher.matches("x".toCharArray(), "not-a-phc-hash"));
    assertFalse(hasher.matches("x".toCharArray(), "$argon2id$v=19$m=19456,t=2,p=1$onlyonefield"));
    assertFalse(hasher.matches("x".toCharArray(), "$bcrypt$v=19$m=1,t=1,p=1$c2FsdA$aGFzaA"));
  }
}
