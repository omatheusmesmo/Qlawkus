package dev.omatheusmesmo.qlawkus.tool.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ToolAccessBuildTest {

    @Inject
    ToolAccessRegistry registry;

    @Test
    void theBuildClassifiedTheToolsOfTheClientByTheirVerb() {
        assertEquals(AccessClass.READ, registry.classOf("readFile"));
        assertEquals(AccessClass.DELETE, registry.classOf("deleteFile"));
        assertEquals(AccessClass.CREATE, registry.classOf("scheduleTask"));
        assertEquals(AccessClass.DELETE, registry.classOf("cancelScheduledTask"));
        assertEquals(AccessClass.EXECUTE, registry.classOf("runCommand"));
    }

    @Test
    void aToolWithNoRecognisedVerbIsExecuteAndKeepsItsFallbackSource() {
        var entry = registry.entry("probeStep").orElseThrow();
        assertEquals(AccessClass.EXECUTE, entry.accessClass());
        assertEquals(AccessSource.FALLBACK, entry.source());
    }

    @Test
    void theBuildWroteTheReportNextToTheOtherBuildOutput() throws IOException {
        Path report = Path.of("target", "qlawkus-tool-access.tsv");
        assertTrue(Files.isRegularFile(report), report.toString());
        assertTrue(Files.readAllLines(report).stream()
                .anyMatch(line -> line.startsWith("readFile\tREAD\tINFERRED\t")));
    }
}
