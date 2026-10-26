package dev.omatheusmesmo.qlawkus.secrets;

import io.quarkus.logging.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.Key;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Single source of truth for the {@code qlawkus.secrets} PKCS12 convention, shared by the read side
 * ({@link KeystoreSecretConfigSourceFactory}) and the write side ({@link KeystoreSecretWriter}).
 *
 * <p>A secret is stored exactly the way {@code keytool -importpass} stores it: a PBE
 * {@link SecretKey} placed in a {@link KeyStore.SecretKeyEntry}. Reading it back with
 * {@code getKey(alias, password).getEncoded()} as UTF-8 returns the original value, so an entry
 * created here is indistinguishable from one created by {@code keytool}. Centralising both sides
 * here keeps the encoding from drifting between reader and writer.
 */
final class KeystoreSecrets {

    private static final String TYPE = "PKCS12";
    private static final boolean SUPPORTS_POSIX =
            FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY_FILE =
            PosixFilePermissions.asFileAttribute(OWNER_ONLY);
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY_DIRECTORY =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));

    private KeystoreSecrets() {
    }

    static Map<String, String> readAll(URL location, char[] password) {
        try (InputStream in = location.openStream()) {
            KeyStore keyStore = KeyStore.getInstance(TYPE);
            keyStore.load(in, password);
            Map<String, String> secrets = new HashMap<>();
            for (String alias : Collections.list(keyStore.aliases())) {
                if (keyStore.isKeyEntry(alias)) {
                    Key key = keyStore.getKey(alias, password);
                    if (key != null) {
                        secrets.put(alias, new String(key.getEncoded(), StandardCharsets.UTF_8));
                    }
                }
            }
            return secrets;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load secrets keystore at " + location, e);
        }
    }

    static List<String> aliases(Path file, char[] password) {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            KeyStore keyStore = load(file, password);
            List<String> aliases = new ArrayList<>(Collections.list(keyStore.aliases()));
            Collections.sort(aliases);
            return aliases;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to list secrets keystore at " + file, e);
        }
    }

    static void put(Path file, char[] password, String alias, String value) {
        try {
            KeyStore keyStore = loadOrCreate(file, password);
            SecretKey secret = SecretKeyFactory.getInstance("PBE")
                    .generateSecret(new PBEKeySpec(value.toCharArray()));
            keyStore.setEntry(alias, new KeyStore.SecretKeyEntry(secret),
                    new KeyStore.PasswordProtection(password));
            save(keyStore, file, password);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to write secret to keystore at " + file, e);
        }
    }

    static boolean delete(Path file, char[] password, String alias) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            KeyStore keyStore = load(file, password);
            if (!keyStore.containsAlias(alias)) {
                return false;
            }
            keyStore.deleteEntry(alias);
            save(keyStore, file, password);
            return true;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to delete secret from keystore at " + file, e);
        }
    }

    static Path resolve(String path) {
        return Path.of(expandHome(path));
    }

    static String expandHome(String path) {
        if (path.startsWith("~")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    private static KeyStore load(Path file, char[] password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(TYPE);
        try (InputStream in = Files.newInputStream(file)) {
            keyStore.load(in, password);
        }
        return keyStore;
    }

    private static KeyStore loadOrCreate(Path file, char[] password) throws Exception {
        if (Files.isRegularFile(file)) {
            return load(file, password);
        }
        KeyStore keyStore = KeyStore.getInstance(TYPE);
        keyStore.load(null, password);
        return keyStore;
    }

    private static void save(KeyStore keyStore, Path file, char[] password) throws Exception {
        Path absolute = file.toAbsolutePath();
        Path dir = absolute.getParent();
        byte[] bytes = serialize(keyStore, password);
        if (!SUPPORTS_POSIX) {
            createDirectories(dir, null);
            Files.write(absolute, bytes);
            return;
        }
        createDirectories(dir, OWNER_ONLY_DIRECTORY);
        if (!replacedThroughTemporaryFile(dir, absolute, bytes)) {
            Files.write(absolute, bytes);
        }
        restrictToOwner(absolute);
    }

    private static byte[] serialize(KeyStore keyStore, char[] password) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        keyStore.store(out, password);
        return out.toByteArray();
    }

    /**
     * Writes the store to an owner-only temporary file beside {@code target} and renames it over the
     * target, so a reader never sees a half-written store. Returns {@code false}, leaving the caller to
     * rewrite the file in place as the write did before, when the rename route is closed: a
     * {@code secrets.p12} bind-mounted into a container on its own sits in a directory the agent cannot
     * create files in, and the mount itself cannot be renamed over ({@code EBUSY}).
     */
    private static boolean replacedThroughTemporaryFile(Path dir, Path target, byte[] bytes) throws IOException {
        Path temp;
        try {
            temp = Files.createTempFile(dir, ".secrets", ".tmp", OWNER_ONLY_FILE);
        } catch (IOException e) {
            return false;
        }
        try {
            Files.write(temp, bytes);
            return moved(temp, target);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static boolean moved(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (AtomicMoveNotSupportedException e) {
            return movedNonAtomically(source, target);
        } catch (FileSystemException e) {
            return false;
        }
    }

    private static boolean movedNonAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (FileSystemException e) {
            return false;
        }
    }

    private static void restrictToOwner(Path file) {
        try {
            Files.setPosixFilePermissions(file, OWNER_ONLY);
        } catch (IOException e) {
            Log.warnf("Secrets keystore %s was written but could not be made owner-only: %s", file, e.getMessage());
        }
    }

    private static void createDirectories(Path dir, FileAttribute<?> attribute) throws IOException {
        if (Files.isDirectory(dir)) {
            return;
        }
        if (attribute == null) {
            Files.createDirectories(dir);
        } else {
            Files.createDirectories(dir, attribute);
        }
    }
}
