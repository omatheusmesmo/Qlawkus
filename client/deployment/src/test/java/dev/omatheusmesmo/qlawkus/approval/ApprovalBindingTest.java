package dev.omatheusmesmo.qlawkus.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ApprovalBindingTest {

    @Test
    void keyOrderDoesNotChangeTheCanonicalArguments() {
        String first = ApprovalBinding.canonicalArguments("{\"b\":2,\"a\":{\"y\":1,\"x\":[3,1]}}");
        String second = ApprovalBinding.canonicalArguments("{ \"a\": { \"x\": [3,1], \"y\": 1 }, \"b\": 2 }");

        assertEquals(first, second);
        assertEquals("{\"a\":{\"x\":[3,1],\"y\":1},\"b\":2}", first);
    }

    @Test
    void blankArgumentsCanonicaliseToAnEmptyObject() {
        assertEquals("{}", ApprovalBinding.canonicalArguments(""));
        assertEquals("{}", ApprovalBinding.canonicalArguments(null));
    }

    @Test
    void anythingButAJsonObjectIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ApprovalBinding.canonicalArguments("[1,2]"));
        assertThrows(IllegalArgumentException.class, () -> ApprovalBinding.canonicalArguments("not json"));
    }

    @Test
    void bindingHashIsStableForTheSameToolAndArguments() {
        String canonical = ApprovalBinding.canonicalArguments("{\"to\":\"ana@example.com\"}");

        assertEquals(ApprovalBinding.hash("sendEmail", canonical), ApprovalBinding.hash("sendEmail", canonical));
        assertEquals(64, ApprovalBinding.hash("sendEmail", canonical).length());
    }

    @Test
    void changingAnArgumentOrTheToolChangesTheHash() {
        String canonical = ApprovalBinding.canonicalArguments("{\"to\":\"ana@example.com\"}");
        String changed = ApprovalBinding.canonicalArguments("{\"to\":\"bia@example.com\"}");

        assertNotEquals(ApprovalBinding.hash("sendEmail", canonical), ApprovalBinding.hash("sendEmail", changed));
        assertNotEquals(ApprovalBinding.hash("sendEmail", canonical), ApprovalBinding.hash("deleteFile", canonical));
    }
}
