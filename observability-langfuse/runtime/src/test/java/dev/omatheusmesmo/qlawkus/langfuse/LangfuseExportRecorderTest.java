package dev.omatheusmesmo.qlawkus.langfuse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkiverse.langfuse.config.LangfuseConfig;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LangfuseExportRecorderTest {

    @Test
    void requiresAConfiguredServerAndBothKeys() {
        assertTrue(configured("https://langfuse.example", "pk-fixture", "sk-fixture"));
        assertTrue(configured("http://localhost:3000", "pk-fixture", "sk-fixture"));
        assertFalse(configured("http://127.0.0.1:1", "pk-fixture", "sk-fixture"));
        assertFalse(configured("https://langfuse.example", "unconfigured", "sk-fixture"));
        assertFalse(configured("https://langfuse.example", "pk-fixture", "unconfigured"));
        assertFalse(configured("relative/path", "pk-fixture", "sk-fixture"));
        assertFalse(configured("not a URL", "pk-fixture", "sk-fixture"));
        assertFalse(configured("https://user:password@langfuse.example", "pk-fixture", "sk-fixture"));
    }

    private static boolean configured(String url, String publicKey, String secretKey) {
        Map<String, String> values = Map.of("baseUrl", url, "publicKey", publicKey, "secretKey", secretKey);
        var config = (LangfuseConfig) Proxy.newProxyInstance(LangfuseConfig.class.getClassLoader(),
                new Class<?>[] { LangfuseConfig.class }, (proxy, method, args) -> values.get(method.getName()));
        return LangfuseExportRecorder.configured(config);
    }
}
