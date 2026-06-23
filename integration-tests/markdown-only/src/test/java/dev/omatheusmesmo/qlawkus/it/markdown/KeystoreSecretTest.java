package dev.omatheusmesmo.qlawkus.it.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

/**
 * Proves the qlawkus-client secret layering end to end in a database-free build: a secret kept in a
 * PKCS12 keystore is injected as an ordinary {@code @ConfigProperty} with no datasource on the
 * classpath, and it is published above {@code application.properties}, so the keystore value wins over
 * a competing {@code application.properties} entry for the same property (the keystore ordinal 350
 * outranks 250, and outranks env at 300). Also pins the leniency contract: an alias that is not in
 * the keystore simply resolves to absent.
 */
@QuarkusTest
class KeystoreSecretTest {

    @Inject
    Config config;

    @ConfigProperty(name = "qlawkus.test.secret")
    String secretFromKeystore;

    @Test
    void keystoreSecretOutranksApplicationProperties() {
        assertEquals("s3cr3t-from-keystore", secretFromKeystore);
    }

    @Test
    void absentAliasIsLenient() {
        assertTrue(config.getOptionalValue("qlawkus.test.missing-secret", String.class).isEmpty());
    }
}
