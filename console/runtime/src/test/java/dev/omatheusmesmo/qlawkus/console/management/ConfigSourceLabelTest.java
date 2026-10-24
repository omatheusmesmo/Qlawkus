package dev.omatheusmesmo.qlawkus.console.management;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConfigSourceLabelTest {

    @Test
    void propertiesFileInAJarIsLabelledByItsFileName() {
        assertEquals("application.properties", ConfigSourceLabel.of(
                "PropertiesConfigSource[source=jar:file:///deployments/app/qlawkus-app-0.9.4-SNAPSHOT.jar!/application.properties]"));
        assertEquals("microprofile-config.properties", ConfigSourceLabel.of(
                "PropertiesConfigSource[source=jar:file:///x/client.jar!/META-INF/microprofile-config.properties]"));
    }

    @Test
    void dotEnvFileIsLabelledDotEnv() {
        assertEquals(".env", ConfigSourceLabel.of("PropertiesConfigSource[source=file:///work/.env]"));
    }

    @Test
    void environmentAndSystemPropertiesGetPlainLabels() {
        assertEquals("env", ConfigSourceLabel.of("EnvConfigSource"));
        assertEquals("system property", ConfigSourceLabel.of("SysPropConfigSource"));
    }

    @Test
    void defaultsAndMissingSourcesAreDefault() {
        assertEquals("default", ConfigSourceLabel.of("DefaultValuesConfigSource"));
        assertEquals("default", ConfigSourceLabel.of(null));
        assertEquals("default", ConfigSourceLabel.of(" "));
    }

    @Test
    void qlawkusSourcesAreNamedForWhatTheyAre() {
        assertEquals("keystore", ConfigSourceLabel.of("QlawkusKeystoreSecrets"));
        assertEquals("override file", ConfigSourceLabel.of("QlawkusRuntimeTogglesOverride"));
        assertEquals("agent.yml", ConfigSourceLabel.of("QlawkusRuntimeTogglesBaked"));
    }

    @Test
    void anUnknownSourceKeepsItsName() {
        assertEquals("ConsulConfigSource", ConfigSourceLabel.of("ConsulConfigSource"));
    }
}
