package dev.omatheusmesmo.qlawkus.tool.shell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StructuredPatchParserTest {

    @Test
    void parsesAddAndUpdate() {
        StructuredPatch patch = StructuredPatchParser.parse("""
                Begin Patch
                Add File: new.txt
                hello
                world
                Update File: existing.txt
                <<<SEARCH
                old
                ===
                new
                >>>
                End Patch
                """);

        assertEquals(2, patch.operations().size());
        assertEquals("hello\nworld",
                ((StructuredPatch.PatchOperation.Add) patch.operations().get(0)).content());
        assertEquals("new",
                ((StructuredPatch.PatchOperation.Update) patch.operations().get(1)).replace());
    }

    @Test
    void rejectsMissingMarkers() {
        assertThrows(StructuredPatchParser.StructuredPatchException.class,
                () -> StructuredPatchParser.parse("Add File: x\n"));
    }
}
