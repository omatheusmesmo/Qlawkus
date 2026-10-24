package dev.omatheusmesmo.qlawkus.console.management;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JavadocTextTest {

    @Test
    void codeTagBecomesACodeElement() {
        assertEquals("Defaults to <code>qlawkus</code>.", JavadocText.toHtml("Defaults to {@code qlawkus}."));
    }

    @Test
    void codeTagKeepsNestedBraces() {
        assertEquals("<code>a{b}c</code>", JavadocText.toHtml("{@code a{b}c}"));
    }

    @Test
    void linkTagRendersTheSimpleNameOfTheTarget() {
        assertEquals("see <code>IdentityProvider</code>", JavadocText.toHtml("see {@link dev.x.IdentityProvider}"));
        assertEquals("see <code>Config#getConfigValue</code>",
                JavadocText.toHtml("see {@link org.eclipse.Config#getConfigValue}"));
        assertEquals("see <code>roots()</code>", JavadocText.toHtml("see {@link #roots()}"));
    }

    @Test
    void linkTagPrefersItsLabel() {
        assertEquals("<code>the index</code>", JavadocText.toHtml("{@link dev.x.ConfigMetadataIndex the index}"));
    }

    @Test
    void literalTagIsPlainEscapedText() {
        assertEquals("a &lt;b&gt;", JavadocText.toHtml("{@literal a <b>}"));
    }

    @Test
    void everythingElseIsEscaped() {
        assertEquals("&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt; &amp; <code>&lt;i&gt;</code>",
                JavadocText.toHtml("<script>alert(\"x\")</script> & {@code <i>}"));
    }

    @Test
    void unknownTagsAndUnclosedTagsStayVisibleAsEscapedText() {
        assertEquals("{@foo x} and {@code open", JavadocText.toHtml("{@foo x} and {@code open"));
    }

    @Test
    void nullAndBlankRenderEmpty() {
        assertEquals("", JavadocText.toHtml(null));
        assertEquals("", JavadocText.toHtml("  "));
    }
}
