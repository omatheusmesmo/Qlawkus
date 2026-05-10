package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class FileConfigTest {

    @Inject
    FileConfig fileConfig;

    @Test
    void maxReadSize_defaultIsPositive() {
        assertTrue(fileConfig.maxReadSize() > 0, "maxReadSize should be positive");
    }

    @Test
    void maxReadSize_defaultIs5MB() {
        assertEquals(5 * 1024 * 1024, fileConfig.maxReadSize(), "maxReadSize should default to 5MB");
    }

    @Test
    void maxWriteSize_defaultIsPositive() {
        assertTrue(fileConfig.maxWriteSize() > 0, "maxWriteSize should be positive");
    }

    @Test
    void maxWriteSize_defaultIs10MB() {
        assertEquals(10 * 1024 * 1024, fileConfig.maxWriteSize(), "maxWriteSize should default to 10MB");
    }

    @Test
    void maxWriteSize_largerThanMaxReadSize() {
        assertTrue(fileConfig.maxWriteSize() >= fileConfig.maxReadSize(),
            "maxWriteSize should be >= maxReadSize");
    }

    @Test
    void encoding_defaultIsUtf8() {
        assertEquals("UTF-8", fileConfig.encoding(), "encoding should default to UTF-8");
    }
}
