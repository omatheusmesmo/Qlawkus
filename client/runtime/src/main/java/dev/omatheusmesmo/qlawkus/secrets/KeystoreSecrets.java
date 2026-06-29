package dev.omatheusmesmo.qlawkus.secrets;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            keyStore.store(out, password);
        }
    }
}
