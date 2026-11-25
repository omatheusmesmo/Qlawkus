package dev.omatheusmesmo.qlawkus.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.config.SecretsConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The zero-configuration keystore password: with no {@code qlawkus.secrets.keystore-password}, the first
 * write generates one into an owner-only file beside the keystore, and later reads and writes reuse it.
 * A configured password always wins and no file is created for it.
 */
class KeystorePasswordFileTest {

    @TempDir
    Path directory;

    @Test
    void theFirstWriteGeneratesAnOwnerOnlyPasswordFileNextToTheKeystore() throws IOException {
        KeystoreSecretWriter writer = writer(Optional.empty());

        writer.setSecret("fixture.alias", "fixture-value");

        Path file = KeystorePasswordFile.locate(keystore());
        assertTrue(Files.isRegularFile(file));
        assertEquals(directory, file.getParent());
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
        assertTrue(Files.readString(file).strip().length() >= 32);
    }

    @Test
    void laterWritersAndReadersReuseTheGeneratedPassword() {
        writer(Optional.empty()).setSecret("fixture.alias", "fixture-value");

        KeystoreSecretWriter another = writer(Optional.empty());
        assertEquals(Optional.of("fixture-value"), another.readSecret("fixture.alias"));
        another.setSecret("second.alias", "second-value");
        assertEquals(Optional.of("second-value"), writer(Optional.empty()).readSecret("second.alias"));
    }

    @Test
    void aConfiguredPasswordWinsAndCreatesNoFile() {
        writer(Optional.of("fixture-store-password")).setSecret("fixture.alias", "fixture-value");

        assertFalse(Files.exists(KeystorePasswordFile.locate(keystore())));
        assertThrows(IllegalStateException.class, () -> writer(Optional.empty()).readSecret("fixture.alias"));
    }

    @Test
    void readingNeverCreatesThePasswordFile() {
        assertTrue(writer(Optional.empty()).readSecret("absent.alias").isEmpty());
        assertFalse(Files.exists(KeystorePasswordFile.locate(keystore())));
    }

    @Test
    void theBootTimeReaderFindsTheGeneratedPassword() {
        writer(Optional.empty()).setSecret("fixture.alias", "fixture-value");

        assertEquals(Optional.of("fixture-value"),
                Optional.ofNullable(KeystoreSecretConfigSourceFactory.readFor(keystore().toString(), null)
                        .get("fixture.alias")));
    }

    @Test
    void concurrentFirstWritersAgreeOnOneCompletePassword() throws Exception {
        int writers = 16;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        try {
            for (int round = 0; round < 40; round++) {
                Path store = directory.resolve("round-" + round + ".p12");
                CyclicBarrier start = new CyclicBarrier(writers);
                List<Future<String>> results = new ArrayList<>();
                for (int i = 0; i < writers; i++) {
                    Callable<String> task = () -> {
                        start.await();
                        return new String(KeystorePasswordFile.readOrCreate(store));
                    };
                    results.add(pool.submit(task));
                }
                String first = results.get(0).get();
                assertTrue(first.length() >= 32);
                for (Future<String> result : results) {
                    assertEquals(first, result.get());
                }
                assertEquals(first, Files.readString(KeystorePasswordFile.locate(store)).strip());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void generatingLeavesNoTemporaryFileBehind() throws IOException {
        writer(Optional.empty()).setSecret("fixture.alias", "fixture-value");

        try (var files = Files.list(directory)) {
            assertEquals(List.of("secrets.p12", "secrets.p12.password"),
                    files.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    private Path keystore() {
        return directory.resolve("secrets.p12");
    }

    private KeystoreSecretWriter writer(Optional<String> password) {
        return new KeystoreSecretWriter(new SecretsConfig() {
            @Override
            public String keystorePath() {
                return keystore().toString();
            }

            @Override
            public Optional<String> keystorePassword() {
                return password;
            }

            @Override
            public int keystoreOrdinal() {
                return 350;
            }
        });
    }
}
