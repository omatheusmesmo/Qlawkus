package dev.omatheusmesmo.qlawkus.it.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

/**
 * Proves the qlawkus-client secret wrapper end to end in a database-free build: only the
 * {@code qlawkus.secrets.*} convention is configured (no raw {@code smallrye.config.source.keystore}
 * lines), the extension auto-wires the keystore source, and a secret kept in a PKCS12 keystore is
 * injected as an ordinary {@code @ConfigProperty} with no datasource on the classpath. Also pins the
 * leniency contract: an alias that is not in the keystore simply resolves to absent.
 */
@QuarkusTest
class KeystoreSecretTest {

    @Inject
    Config config;

    @ConfigProperty(name = "qlawkus.test.secret")
    String secretFromKeystore;

    @Test
    void resolvesSecretFromKeystoreViaWrapperConvention() {
        assertEquals("s3cr3t-from-keystore", secretFromKeystore);
    }

    @Test
    void absentAliasIsLenient() {
        assertTrue(config.getOptionalValue("qlawkus.test.missing-secret", String.class).isEmpty());
    }
}
