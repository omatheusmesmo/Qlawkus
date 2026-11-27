package dev.omatheusmesmo.qlawkus.tool.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ToolAccessRegistryTest {

    private final ToolAccessRegistry registry = new ToolAccessRegistry(List.of(
            new ToolAccessEntry("sendEmail", AccessClass.SEND, AccessSource.INFERRED, "GmailTool#sendEmail"),
            new ToolAccessEntry("listEvents", AccessClass.READ, AccessSource.INFERRED, "CalendarTool#listEvents")));

    @Test
    void aKnownToolIsFoundWithItsClass() {
        assertEquals(Optional.of(AccessClass.SEND), registry.find("sendEmail"));
        assertEquals(AccessClass.READ, registry.classOf("listEvents"));
    }

    @Test
    void anUnknownToolIsTheMostRestrictiveClass() {
        assertTrue(registry.find("mcp_remote_tool").isEmpty());
        assertEquals(AccessClass.EXECUTE, registry.classOf("mcp_remote_tool"));
    }

    @Test
    void entriesComeBackSortedByToolName() {
        assertEquals(List.of("listEvents", "sendEmail"),
                registry.entries().stream().map(ToolAccessEntry::tool).toList());
    }
}
