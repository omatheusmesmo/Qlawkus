package dev.omatheusmesmo.qlawkus.secrets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * The keystore password of a deployment that configures none: a random value kept in an owner-only file
 * beside the keystore, so a container with one volume needs no environment file to use the secret store.
 *
 * <p>This is a convenience, not a substitute for a configured {@code qlawkus.secrets.keystore-password}:
 * the file sits on the same volume as the keystore, so it protects against the keystore being copied
 * alone, not against whoever can read the whole volume. A configured password always wins and no file is
 * created for it. Reading never creates the file; only the first write does.
 *
 * <p>The file appears complete or not at all: the password is written to an owner-only temporary file
 * beside it and then published by a hard link, which fails when the file already exists, so concurrent
 * first writers agree on one password and a crash never leaves an empty file behind.
 */
final class KeystorePasswordFile {

    private static final int PASSWORD_BYTES = 32;

    private KeystorePasswordFile() {
    }

    static Path locate(Path keystore) {
        Path absolute = keystore.toAbsolutePath();
        return absolute.resolveSibling(absolute.getFileName() + ".password");
    }

    static Optional<char[]> read(Path keystore) {
        Path file = locate(keystore);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String value = Files.readString(file, StandardCharsets.UTF_8).strip();
            return value.isEmpty() ? Optional.empty() : Optional.of(value.toCharArray());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read the keystore password file " + file, e);
        }
    }

    static char[] readOrCreate(Path keystore) {
        Optional<char[]> existing = read(keystore);
        if (existing.isPresent()) {
            return existing.get();
        }
        Path file = locate(keystore);
        byte[] bytes = new byte[PASSWORD_BYTES];
        new SecureRandom().nextBytes(bytes);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create the keystore directory " + file.getParent(), e);
        }
        Path temporary = null;
        try {
            temporary = Files.createTempFile(file.getParent(), file.getFileName() + ".", ".tmp");
            Files.writeString(temporary, password, StandardCharsets.UTF_8);
            publish(temporary, file);
        } catch (FileAlreadyExistsException raced) {
            return read(keystore).orElseThrow(() -> new IllegalStateException(
                    "The keystore password file " + file + " exists but is empty", raced));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create the keystore password file " + file, e);
        } finally {
            discard(temporary);
        }
        return password.toCharArray();
    }

    /**
     * Gives {@code file} the complete content of {@code temporary} without replacing an existing file.
     * A hard link does it in one step; a filesystem without links falls back to a move, which also
     * refuses to replace.
     */
    private static void publish(Path temporary, Path file) throws IOException {
        try {
            Files.createLink(file, temporary);
        } catch (FileAlreadyExistsException raced) {
            throw raced;
        } catch (IOException | UnsupportedOperationException noLinks) {
            Files.move(temporary, file);
        }
    }

    private static void discard(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            temporary.toFile().deleteOnExit();
        }
    }
}
