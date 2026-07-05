package dev.omatheusmesmo.qlawkus.security;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Hashes and verifies passwords with Argon2id, database-free, using the Bouncy Castle low-level
 * generator (pure Java, so it survives GraalVM native compilation without JNA or a native library).
 *
 * <p>Hashes are the standard PHC string
 * {@code $argon2id$v=19$m=<mem>,t=<time>,p=<par>$<b64-salt>$<b64-hash>}, which is self-describing:
 * {@link #matches} reads the cost parameters and salt back from the stored string, so a stored hash
 * stays verifiable even if the defaults here change later. Parameters follow the OWASP Argon2id
 * baseline (19 MiB, 2 iterations, 1 lane). Comparison is constant-time.
 */
public final class Argon2idPasswordHasher {

  private static final int DEFAULT_MEMORY_KB = 19_456;
  private static final int DEFAULT_ITERATIONS = 2;
  private static final int DEFAULT_PARALLELISM = 1;
  private static final int SALT_LENGTH = 16;
  private static final int HASH_LENGTH = 32;

  private final SecureRandom random = new SecureRandom();

  /** Hashes a password with the default parameters, returning a PHC {@code $argon2id$...} string. */
  public String hash(char[] password) {
    byte[] salt = new byte[SALT_LENGTH];
    random.nextBytes(salt);
    Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withMemoryAsKB(DEFAULT_MEMORY_KB)
        .withIterations(DEFAULT_ITERATIONS)
        .withParallelism(DEFAULT_PARALLELISM)
        .withSalt(salt)
        .build();
    byte[] hash = generate(params, password, HASH_LENGTH);
    return encode(params, salt, hash);
  }

  /**
   * Verifies a password against a stored PHC hash, reading the cost parameters and salt from it. A
   * malformed hash is treated as a non-match, never a crash.
   */
  public boolean matches(char[] password, String storedHash) {
    Parsed parsed;
    try {
      parsed = parse(storedHash);
    } catch (RuntimeException malformed) {
      return false;
    }
    byte[] recomputed = generate(parsed.params, password, parsed.expectedHash.length);
    return MessageDigest.isEqual(recomputed, parsed.expectedHash);
  }

  private static byte[] generate(Argon2Parameters params, char[] password, int length) {
    Argon2BytesGenerator generator = new Argon2BytesGenerator();
    generator.init(params);
    byte[] out = new byte[length];
    byte[] passwordBytes = toUtf8(password);
    try {
      generator.generateBytes(passwordBytes, out);
    } finally {
      Arrays.fill(passwordBytes, (byte) 0);
    }
    return out;
  }

  private static String encode(Argon2Parameters params, byte[] salt, byte[] hash) {
    Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
    return "$argon2id$v=" + params.getVersion()
        + "$m=" + params.getMemory() + ",t=" + params.getIterations() + ",p=" + params.getLanes()
        + "$" + b64.encodeToString(salt)
        + "$" + b64.encodeToString(hash);
  }

  private static Parsed parse(String phc) {
    String[] parts = phc.split("\\$");
    if (parts.length != 6 || !parts[1].equals("argon2id")) {
      throw new IllegalArgumentException("not an argon2id PHC hash");
    }
    int version = parseTagged(parts[2], "v");
    String[] costs = parts[3].split(",");
    int memory = parseTagged(costs[0], "m");
    int iterations = parseTagged(costs[1], "t");
    int parallelism = parseTagged(costs[2], "p");
    Base64.Decoder b64 = Base64.getDecoder();
    byte[] salt = b64.decode(parts[4]);
    byte[] expectedHash = b64.decode(parts[5]);
    Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(version)
        .withMemoryAsKB(memory)
        .withIterations(iterations)
        .withParallelism(parallelism)
        .withSalt(salt)
        .build();
    return new Parsed(params, expectedHash);
  }

  private static int parseTagged(String field, String key) {
    String prefix = key + "=";
    if (!field.startsWith(prefix)) {
      throw new IllegalArgumentException("expected " + key + "= in '" + field + "'");
    }
    return Integer.parseInt(field.substring(prefix.length()));
  }

  private static byte[] toUtf8(char[] chars) {
    CharBuffer charBuffer = CharBuffer.wrap(chars);
    ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
    byte[] bytes = new byte[byteBuffer.remaining()];
    byteBuffer.get(bytes);
    return bytes;
  }

  private record Parsed(Argon2Parameters params, byte[] expectedHash) {
  }
}
