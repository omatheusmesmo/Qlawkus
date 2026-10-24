package dev.omatheusmesmo.qlawkus.secrets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellSecretEnvCatalogTest {

    @Test
    void shellSecretEnvEntriesAreSecretWhetherQuotedOrNot() {
        assertTrue(SecretPropertyCatalog.isSecret("qlawkus.shell.secret-env.\"GH_TOKEN\""));
        assertTrue(SecretPropertyCatalog.isSecret("qlawkus.shell.secret-env.GH_TOKEN"));
        assertTrue(SecretPropertyCatalog.isSecret("qlawkus.shell.secret-env.\"NPM_TOKEN\""));
    }

    @Test
    void otherShellPropertiesStayEditable() {
        assertFalse(SecretPropertyCatalog.isSecret("qlawkus.shell.env-allowlist"));
        assertFalse(SecretPropertyCatalog.isSecret("qlawkus.shell.max-concurrent"));
    }
}
